package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CoachRunJpaRepository extends JpaRepository<CoachRunEntity, UUID> {
    boolean existsByLockKey(String lockKey);

    /**
     * 멈춘 RUNNING 정리(까닭 코드 STALE). 스케줄 스레드에서 트랜잭션 없이 불리므로 이 문장이 스스로 트랜잭션을 연다. 잠금도 푼다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CoachRunEntity r
               set r.status = 'FAILED', r.failureCode = 'STALE', r.failureReason = :reason, r.lockKey = null,
                   r.updatedAt = :at
             where r.status = 'RUNNING' and r.createdAt < :before
            """)
    int failRunningCreatedBefore(
            @Param("before") Instant before, @Param("reason") String reason, @Param("at") Instant at);

    /** 한 (프로필, 날짜) 잠금을 잡은 멈춘 RUNNING 만 정리한다(까닭 코드 STALE). 편성 시작 트랜잭션 안에서 부른다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CoachRunEntity r
               set r.status = 'FAILED', r.failureCode = 'STALE', r.failureReason = :reason, r.lockKey = null,
                   r.updatedAt = :at
             where r.lockKey = :lockKey and r.status = 'RUNNING' and r.createdAt < :before
            """)
    int failStaleLock(
            @Param("lockKey") String lockKey,
            @Param("before") Instant before,
            @Param("reason") String reason,
            @Param("at") Instant at);

    /** 새 편성이 들어오면 같은 (프로필, 날짜)의 승인 대기 제안을 거절로 바꾼다(결정 1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CoachRunEntity r
               set r.status = 'REJECTED', r.rejectedReason = :reason, r.rejectedAt = :at, r.updatedAt = :at
             where r.subjectProfileId = :subjectProfileId and r.runDate = :runDate and r.status = 'AWAITING_APPROVAL'
            """)
    int rejectAwaitingOf(
            @Param("subjectProfileId") UUID subjectProfileId,
            @Param("runDate") LocalDate runDate,
            @Param("reason") String reason,
            @Param("at") Instant at);

    /** AI 접수 번호를 RUNNING 일 때만 남긴다. 잠금 키는 건드리지 않는다 — 정리된 실행의 잠금을 되살리지 않게. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CoachRunEntity r
               set r.aiRunId = :aiRunId, r.updatedAt = :at
             where r.id = :id and r.status = 'RUNNING'
            """)
    int attachAiRunIfRunning(@Param("id") UUID id, @Param("aiRunId") String aiRunId, @Param("at") Instant at);

    /**
     * 파이프라인 결과(AWAITING_APPROVAL · FAILED)를 RUNNING 일 때만 쓰고 잠금을 푼다.
     * 파이프라인이 읽은 뒤 정리 작업 · 새 요청이 먼저 FAILED 로 커밋했으면 0행이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CoachRunEntity r
               set r.status = :status, r.summary = :summary, r.stepsJson = :stepsJson,
                   r.proposalJson = :proposalJson, r.modelName = :modelName, r.failureCode = :failureCode,
                   r.failureReason = :failureReason, r.aiRefused = :aiRefused, r.aiRefusalReason = :aiRefusalReason,
                   r.lockKey = null, r.updatedAt = :at
             where r.id = :id and r.status = 'RUNNING'
            """)
    int finishIfRunning(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("summary") @Nullable String summary,
            @Param("stepsJson") @Nullable String stepsJson,
            @Param("proposalJson") @Nullable String proposalJson,
            @Param("modelName") @Nullable String modelName,
            @Param("failureCode") @Nullable String failureCode,
            @Param("failureReason") @Nullable String failureReason,
            @Param("aiRefused") boolean aiRefused,
            @Param("aiRefusalReason") @Nullable String aiRefusalReason,
            @Param("at") Instant at);

    @Nullable
    CoachRunEntity findFirstByFamilyIdAndWeekStartOrderByCreatedAtDesc(UUID familyId, LocalDate weekStart);

    @Nullable
    CoachRunEntity findFirstByFamilyIdOrderByCreatedAtDesc(UUID familyId);

    @Nullable
    CoachRunEntity findFirstByFamilyIdAndSubjectProfileIdOrderByCreatedAtDesc(UUID familyId, UUID subjectProfileId);

    @Query("select r.status from CoachRunEntity r where r.id = :id")
    @Nullable
    String statusOf(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CoachRunEntity r
               set r.status = 'APPROVED', r.approvedBy = :approvedBy, r.approvedAt = :approvedAt, r.updatedAt = :approvedAt
             where r.id = :id and r.status = 'AWAITING_APPROVAL'
            """)
    int approveIfAwaiting(
            @Param("id") UUID id, @Param("approvedBy") UUID approvedBy, @Param("approvedAt") Instant approvedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CoachRunEntity r
               set r.status = 'REJECTED', r.rejectedReason = :reason, r.rejectedAt = :rejectedAt, r.updatedAt = :rejectedAt
             where r.id = :id and r.status = 'AWAITING_APPROVAL'
            """)
    int rejectIfAwaiting(
            @Param("id") UUID id, @Param("reason") @Nullable String reason, @Param("rejectedAt") Instant rejectedAt);
}
