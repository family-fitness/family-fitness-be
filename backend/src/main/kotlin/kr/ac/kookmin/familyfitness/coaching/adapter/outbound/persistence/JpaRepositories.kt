package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface CoachRunJpaRepository : JpaRepository<CoachRunEntity, UUID> {
    fun existsByFamilyIdAndStatus(
        familyId: UUID,
        status: String,
    ): Boolean

    fun existsByFamilyIdAndWeekStartAndStatusIn(
        familyId: UUID,
        weekStart: LocalDate,
        statuses: Collection<String>,
    ): Boolean

    fun findFirstByFamilyIdAndWeekStartOrderByCreatedAtDesc(
        familyId: UUID,
        weekStart: LocalDate,
    ): CoachRunEntity?

    @Query("select r.status from CoachRunEntity r where r.id = :id")
    fun statusOf(
        @Param("id") id: UUID,
    ): String?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CoachRunEntity r
           set r.status = 'APPROVED', r.approvedBy = :approvedBy, r.approvedAt = :approvedAt, r.updatedAt = :approvedAt
         where r.id = :id and r.status = 'AWAITING_APPROVAL'
        """,
    )
    fun approveIfAwaiting(
        @Param("id") id: UUID,
        @Param("approvedBy") approvedBy: UUID,
        @Param("approvedAt") approvedAt: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CoachRunEntity r
           set r.status = 'REJECTED', r.rejectedReason = :reason, r.rejectedAt = :rejectedAt, r.updatedAt = :rejectedAt
         where r.id = :id and r.status = 'AWAITING_APPROVAL'
        """,
    )
    fun rejectIfAwaiting(
        @Param("id") id: UUID,
        @Param("reason") reason: String?,
        @Param("rejectedAt") rejectedAt: Instant,
    ): Int
}

interface CoachRunProposalItemJpaRepository : JpaRepository<CoachRunProposalItemEntity, ProposalItemId> {
    fun findByIdCoachRunIdOrderByIdPosition(coachRunId: UUID): List<CoachRunProposalItemEntity>

    fun deleteByIdCoachRunId(coachRunId: UUID)
}

interface MissionJpaRepository : JpaRepository<MissionEntity, UUID> {
    fun findByFamilyId(familyId: UUID): List<MissionEntity>

    fun findByFamilyIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
        familyId: UUID,
        to: LocalDate,
        from: LocalDate,
    ): List<MissionEntity>

    fun countByCoachRunId(coachRunId: UUID): Long
}

interface MissionParticipantJpaRepository : JpaRepository<MissionParticipantEntity, MissionParticipantId> {
    fun findByIdMissionId(missionId: UUID): List<MissionParticipantEntity>

    fun findByIdMissionIdIn(missionIds: Collection<UUID>): List<MissionParticipantEntity>
}

interface ExerciseVideoJpaRepository : JpaRepository<ExerciseVideoEntity, String> {
    fun findByVideoIdGreaterThanOrderByVideoIdAsc(afterVideoId: String): List<ExerciseVideoEntity>

    fun findAllByOrderByVideoIdAsc(): List<ExerciseVideoEntity>
}

interface VideoInteractionJpaRepository : JpaRepository<VideoInteractionEntity, UUID> {
    fun findByProfileIdAndVideoId(
        profileId: UUID,
        videoId: String,
    ): VideoInteractionEntity?

    fun findByProfileId(profileId: UUID): List<VideoInteractionEntity>
}

interface CoachMessageJpaRepository : JpaRepository<CoachMessageEntity, UUID> {
    fun findFirstByConversationIdOrderByCreatedAtAsc(conversationId: UUID): CoachMessageEntity?
}

interface CoachMessageCitationJpaRepository : JpaRepository<CoachMessageCitationEntity, CoachMessageCitationId>
