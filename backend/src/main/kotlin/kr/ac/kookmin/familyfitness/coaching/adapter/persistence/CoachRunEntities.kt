package kr.ac.kookmin.familyfitness.coaching.adapter.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** `coach_runs` 행. 도메인 [kr.ac.kookmin.familyfitness.coaching.domain.CoachRun] 과는 매퍼로만 오간다. */
@Entity
@Table(name = "coach_runs")
class CoachRunEntity(
    @Id
    @Column(name = "id")
    val id: UUID,
    @Column(name = "family_id", nullable = false)
    val familyId: UUID,
    @Column(name = "week_start", nullable = false)
    val weekStart: LocalDate,
    @Column(name = "trigger_type", nullable = false, length = 10)
    val triggerType: String,
    @Column(name = "status", nullable = false, length = 20)
    var status: String,
    @Column(name = "summary")
    var summary: String?,
    @Column(name = "steps_json")
    var stepsJson: String?,
    @Column(name = "proposal_json")
    var proposalJson: String?,
    @Column(name = "ai_run_id", length = 40)
    var aiRunId: String?,
    @Column(name = "model_name", length = 40)
    var modelName: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "days_per_week", nullable = false)
    val daysPerWeek: Int,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "minutes_per_session", nullable = false)
    val minutesPerSession: Int,
    @Column(name = "requested_by_profile_id")
    val requestedByProfileId: UUID?,
    @Column(name = "approved_by")
    var approvedBy: UUID?,
    @Column(name = "approved_at")
    var approvedAt: Instant?,
    @Column(name = "rejected_reason", length = 300)
    var rejectedReason: String?,
    @Column(name = "rejected_at")
    var rejectedAt: Instant?,
    @Column(name = "failure_reason", length = 300)
    var failureReason: String?,
    @Column(name = "ai_refused", nullable = false)
    var aiRefused: Boolean,
    @Column(name = "ai_refusal_reason", length = 60)
    var aiRefusalReason: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

@Embeddable
data class ProposalItemId(
    @Column(name = "coach_run_id")
    val coachRunId: UUID,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position")
    val position: Int,
) : Serializable

/** `coach_run_proposal_items` 행. participants·citations 는 JSON 텍스트로 보관한다. */
@Entity
@Table(name = "coach_run_proposal_items")
class CoachRunProposalItemEntity(
    @EmbeddedId
    val id: ProposalItemId,
    @Column(name = "title", nullable = false, length = 120)
    val title: String,
    @Column(name = "description", length = 400)
    val description: String?,
    @Column(name = "rationale", length = 400)
    val rationale: String?,
    @Column(name = "target_metric", nullable = false, length = 20)
    val targetMetric: String,
    @Column(name = "target_value", nullable = false)
    val targetValue: Int,
    @Column(name = "video_id", length = 32)
    val videoId: String?,
    @Column(name = "video_start_sec")
    val videoStartSec: Int?,
    @Column(name = "starts_on", nullable = false)
    val startsOn: LocalDate,
    @Column(name = "ends_on", nullable = false)
    val endsOn: LocalDate,
    @Column(name = "copy_child", length = 400)
    val copyChild: String?,
    @Column(name = "copy_parent", length = 400)
    val copyParent: String?,
    @Column(name = "participants_json", nullable = false)
    val participantsJson: String,
    @Column(name = "citations_json", nullable = false)
    val citationsJson: String,
)
