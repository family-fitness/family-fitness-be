package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoachRunJpaRepository extends JpaRepository<CoachRunEntity, UUID> {
    boolean existsByFamilyIdAndStatus(UUID familyId, String status);

    boolean existsByFamilyIdAndWeekStartAndStatusIn(UUID familyId, LocalDate weekStart, Collection<String> statuses);

    @Nullable
    CoachRunEntity findFirstByFamilyIdAndWeekStartOrderByCreatedAtDesc(UUID familyId, LocalDate weekStart);

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
