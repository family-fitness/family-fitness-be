package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "missions")
class MissionEntity(
    @Id
    @Column(name = "id")
    val id: UUID,
    @Column(name = "family_id", nullable = false)
    val familyId: UUID,
    @Column(name = "coach_run_id")
    val coachRunId: UUID?,
    @Column(name = "title", nullable = false, length = 120)
    val title: String,
    @Column(name = "description", length = 400)
    val description: String?,
    @Column(name = "origin", nullable = false, length = 10)
    val origin: String,
    @Column(name = "target_metric", nullable = false, length = 20)
    val targetMetric: String,
    @Column(name = "target_value", nullable = false)
    val targetValue: Int,
    @Column(name = "video_id", length = 32)
    val videoId: String?,
    @Column(name = "video_start_sec")
    val videoStartSec: Int?,
    @Column(name = "rationale", length = 400)
    val rationale: String?,
    @Column(name = "starts_on", nullable = false)
    val startsOn: LocalDate,
    @Column(name = "ends_on", nullable = false)
    val endsOn: LocalDate,
    @Column(name = "created_by")
    val createdBy: UUID?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

@Embeddable
data class MissionParticipantId(
    @Column(name = "mission_id")
    val missionId: UUID,
    @Column(name = "profile_id")
    val profileId: UUID,
) : Serializable

@Entity
@Table(name = "mission_participants")
class MissionParticipantEntity(
    @EmbeddedId
    val id: MissionParticipantId,
    @Column(name = "status", nullable = false, length = 20)
    var status: String,
    @Column(name = "progress", nullable = false, precision = 4, scale = 3)
    var progress: BigDecimal,
    @Column(name = "verified_by", length = 20)
    var verifiedBy: String?,
    @Column(name = "verified_at")
    var verifiedAt: Instant?,
    @Column(name = "confirmed_by_profile_id")
    var confirmedByProfileId: UUID?,
    @Column(name = "coach_role", length = 10)
    val coachRole: String?,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
