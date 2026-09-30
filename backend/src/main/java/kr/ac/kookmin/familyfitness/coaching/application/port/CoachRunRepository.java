package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import org.jspecify.annotations.Nullable;

/**
 * 코치 실행 저장소(아웃바운드 포트). 제안 항목은 실행과 함께 저장·복원된다.
 * 이미 있는 실행의 상태는 조건부 UPDATE(…IfRunning · …IfAwaiting)로만 바꾼다. 행 전체를 덮어쓰는 갱신은 없다 —
 * 읽은 뒤 커밋 전에 다른 트랜잭션(정리 작업 · 새 요청 · 동시 승인)이 바꾼 상태를 되돌려 쓰지 않게.
 */
public interface CoachRunRepository {
    /** 새 실행을 넣는다. 같은 id 가 이미 있으면 IllegalStateException. */
    CoachRun save(CoachRun run);

    /**
     * 새 RUNNING 실행을 넣고 곧바로 반영한다. 같은 잠금 키({@link CoachRun#lockKey()})를 다른 요청이 먼저 잡았으면
     * (유니크 인덱스 위반) 넣지 않고 false. 동시에 들어온 두 요청 중 하나만 true 다.
     */
    boolean insertRunning(CoachRun run);

    @Nullable
    CoachRun findById(UUID id);

    @Nullable
    CoachRunStatus currentStatus(UUID id);

    /** 이 잠금 키를 잡은 RUNNING 이 있는가. */
    boolean isLocked(String lockKey);

    /**
     * 이 잠금 키를 잡은 채 before 보다 먼저 만들어진 RUNNING 을 FAILED 로 바꾸고 잠금을 푼다. 바꾼 행 수.
     * 서버가 끝내지 못한 실행이 그 (프로필, 날짜)의 새 편성을 막지 않게 한다.
     */
    int failStaleLock(String lockKey, Instant before, String reason, Instant at);

    /**
     * `where status = 'RUNNING' and created_at < :before` 조건부 UPDATE 로 FAILED 로 바꾸고 잠금을 푼다. 바꾼 행 수.
     * 그 사이 파이프라인이 끝낸 실행은 조건에 걸리지 않는다.
     */
    int failRunningCreatedBefore(Instant before, String reason, Instant at);

    /**
     * 도메인 attachAiRun 뒤의 run 이 가진 AI 접수 번호를 `where status = 'RUNNING'` 조건부 UPDATE 로 남긴다.
     * 잠금 키는 건드리지 않는다. 그 사이 FAILED 로 정리된 실행이면 0행 → false.
     */
    boolean attachAiRunIfRunning(CoachRun run);

    /**
     * 도메인 complete · fail 뒤의 run 을 `where status = 'RUNNING'` 조건부 UPDATE 한 문장으로 반영하고 잠금을 푼다.
     * 1행을 바꿨을 때만 제안 항목을 쓴다. 파이프라인이 읽은 뒤 정리 작업 · 새 요청이 먼저 FAILED 로 커밋했으면
     * 0행 → false 이고 아무것도 쓰지 않는다.
     */
    boolean finishIfRunning(CoachRun run);

    /** 같은 (프로필, 날짜)의 AWAITING_APPROVAL 을 조건부 UPDATE 로 REJECTED 로 바꾼다. 바꾼 행 수. */
    int rejectAwaitingOf(UUID subjectProfileId, LocalDate runDate, String reason, Instant at);

    /** 그 주(weekStart) 실행 중 가장 최근 것. */
    @Nullable
    CoachRun findLatestOfWeek(UUID familyId, LocalDate weekStart);

    /**
     * 가족의 가장 최근 실행(상태와 상관없이). 다만 APPROVED 인데 그 실행으로 만든 미션이 하나도 남지 않은 실행(보호자가 모두 지움)은
     * 건너뛴다 — latest 에 보일 실행이다.
     */
    @Nullable
    CoachRun findLatestShownOfFamily(UUID familyId);

    /** 그 가족에서 이 프로필을 대상으로 짠 가장 최근 실행. 건너뛰는 실행은 {@link #findLatestShownOfFamily} 와 같다. */
    @Nullable
    CoachRun findLatestShownOfSubject(UUID familyId, UUID subjectProfileId);

    /**
     * 승인 결과를 조건부 UPDATE(`where status = 'AWAITING_APPROVAL'`) 한 문장으로 반영한다.
     * 동시 승인 경합에서 한쪽만 1행을 바꾼다. 0행이면 false.
     */
    boolean approveIfAwaiting(CoachRun run);

    boolean rejectIfAwaiting(CoachRun run);
}
