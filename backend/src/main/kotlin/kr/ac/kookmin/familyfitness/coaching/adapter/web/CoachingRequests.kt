package kr.ac.kookmin.familyfitness.coaching.adapter.web

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StartCoachRunRequest(
    val weekStart: LocalDate? = null,
    @field:Min(1) @field:Max(7)
    val daysPerWeek: Int = 3,
    @field:Min(5) @field:Max(60)
    val minutesPerSession: Int = 15,
)

data class RejectCoachRunRequest(
    @field:Size(max = 300)
    val reason: String? = null,
)

data class CreateMissionRequest(
    @field:NotBlank @field:Size(min = 1, max = 50)
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val targetMetric: TargetMetric,
    @field:Min(1)
    val targetValue: Int,
    val videoId: String? = null,
    @field:NotEmpty @field:Size(min = 1, max = 5)
    val participantProfileIds: List<UUID>,
)

data class RecordStepsRequest(
    val profileId: UUID,
    val activityDate: LocalDate,
    @field:Min(0) @field:Max(100_000)
    val steps: Int,
)

data class RecordTimerRequest(
    val profileId: UUID,
    val startedAt: Instant,
    val endedAt: Instant,
    @field:Min(1) @field:Max(180)
    val activeMinutes: Int,
)

data class FavoriteRequest(
    val profileId: UUID,
    val favorited: Boolean,
)

data class VideoProgressRequest(
    val profileId: UUID,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0")
    val progress: Double,
    @field:Min(0)
    val watchedSec: Int,
    val missionId: UUID? = null,
)

data class ChatRequest(
    val profileId: UUID,
    val conversationId: UUID? = null,
    @field:NotBlank @field:Size(min = 1, max = 500)
    val question: String,
)
