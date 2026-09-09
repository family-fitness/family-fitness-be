package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StartCoachRunCommand(
    val weekStart: LocalDate?,
    val daysPerWeek: Int = 3,
    val minutesPerSession: Int = 15,
)

/** 202 응답. */
data class CoachRunAcceptedView(
    val coachRunId: UUID,
    val status: CoachRunStatus,
    val pollAfterMs: Int,
)

data class CoachRunView(
    val coachRunId: UUID,
    val familyId: UUID,
    val status: CoachRunStatus,
    val weekStart: LocalDate,
    val summary: String?,
    val steps: List<CoachStep>,
    val proposals: List<ProposalView>?,
    val canApprove: Boolean,
    val missionCount: Int,
    val rejectedReason: String?,
)

data class ProposalView(
    val position: Int,
    val title: String,
    val rationale: String?,
    val targetMetric: String,
    val targetValue: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val participants: List<ProposalParticipantView>,
    val video: ProposalVideoView?,
    val citations: List<ProposalCitationView>,
)

data class ProposalParticipantView(
    val profileId: UUID,
    val role: ProfileRole,
    val coachRole: String,
)

data class ProposalVideoView(
    val videoId: String,
    val title: String?,
    val url: String,
    val startSec: Int?,
    val badges: List<String>,
)

data class ProposalCitationView(
    val index: Int,
    val label: String,
    val chunkId: String,
    val url: String?,
)

data class ApproveCoachRunView(
    val coachRunId: UUID,
    val status: CoachRunStatus,
    val approvedBy: UUID,
    val approvedAt: Instant,
    val createdMissions: List<CreatedMissionView>,
)

data class CreatedMissionView(
    val missionId: UUID,
    val title: String,
    val origin: MissionOrigin,
)

data class RejectCoachRunView(
    val coachRunId: UUID,
    val status: CoachRunStatus,
    val rejectedReason: String?,
    val missionCount: Int,
)
