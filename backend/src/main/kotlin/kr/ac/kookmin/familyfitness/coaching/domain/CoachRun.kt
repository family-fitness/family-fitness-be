package kr.ac.kookmin.familyfitness.coaching.domain

import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** `RUNNING` → `AWAITING_APPROVAL` → `APPROVED` / `REJECTED`; `RUNNING` → `FAILED`. */
enum class CoachRunStatus {
    RUNNING,
    AWAITING_APPROVAL,
    APPROVED,
    REJECTED,
    FAILED,
}

enum class TriggerType {
    SCHEDULE,
    MANUAL,
}

/**
 * 승인·거절을 시도하는 사람. HTTP 요청의 role 값이 아니라 identity 조회 결과(부모 프로필)로 만든다.
 * [profileId] 는 승인하는 보호자의 프로필 id 이며 `coach_runs.approved_by` 에 그대로 남는다.
 */
data class CoachApprover(
    val profileId: UUID,
    val familyId: UUID,
    val isParent: Boolean,
)

/** AI 편성 파이프라인의 단계 기록(assess · retrieve · compose · verify). */
data class CoachStep(
    val seq: Int,
    val name: String,
    val status: String,
    val summary: String,
)

/** 편성 역할: CHILD → 주행자 · PARENT(WEEKEND·FULL) → 동반자 · PARENT(CHEER_ONLY·null) → 응원. */
object CoachRoles {
    const val DRIVER = "주행자"
    const val COMPANION = "동반자"
    const val CHEER = "응원"

    fun of(
        role: ProfileRole,
        supportMode: SupportMode?,
    ): String =
        when {
            role == ProfileRole.CHILD -> DRIVER
            supportMode == null -> CHEER
            supportMode == SupportMode.CHEER_ONLY -> CHEER
            else -> COMPANION
        }
}

data class ProposalParticipant(
    val profileId: UUID,
    val role: ProfileRole,
    val coachRole: String,
)

data class ProposalVideo(
    val videoId: String,
    val startSec: Int?,
)

data class ProposalCitation(
    val index: Int,
    val label: String,
    val chunkId: String,
    val url: String?,
)

/** 승인 대기 중인 코치 제안의 항목. 승인되면 이 값이 그대로 [Mission] 으로 복사된다. */
data class CoachProposalItem(
    val position: Int,
    val title: String,
    val targetMetric: String,
    val targetValue: Int,
    val rationale: String? = null,
    val description: String? = null,
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
    val participants: List<ProposalParticipant> = emptyList(),
    val video: ProposalVideo? = null,
    val citations: List<ProposalCitation> = emptyList(),
    val copyChild: String? = null,
    val copyParent: String? = null,
)

/**
 * 주간 코치가 제안을 만든 한 번의 실행. 승인 전에는 미션이 아니다.
 * 상태 전이와 승인 권한 규칙은 전부 여기에 있고, 애플리케이션은 트랜잭션과 포트 호출만 조합한다.
 */
class CoachRun private constructor(
    val id: UUID,
    val familyId: UUID,
    val weekStart: LocalDate,
    val triggerType: TriggerType,
    val daysPerWeek: Int,
    val minutesPerSession: Int,
    val requestedBy: UUID?,
    val createdAt: Instant,
    status: CoachRunStatus,
    proposals: List<CoachProposalItem>,
    steps: List<CoachStep>,
    summary: String?,
    proposalJson: String?,
    aiRunId: String?,
    modelName: String?,
    approvedBy: UUID?,
    approvedAt: Instant?,
    rejectedReason: String?,
    rejectedAt: Instant?,
    failureReason: String?,
    aiRefused: Boolean,
    aiRefusalReason: String?,
    updatedAt: Instant,
) {
    var status: CoachRunStatus = status
        private set
    var proposals: List<CoachProposalItem> = proposals.sortedBy { it.position }
        private set
    var steps: List<CoachStep> = steps
        private set
    var summary: String? = summary
        private set

    /** AI 가 돌려준 proposal 원문. 감사·재변환용이며 화면에는 나가지 않는다. */
    var proposalJson: String? = proposalJson
        private set
    var aiRunId: String? = aiRunId
        private set
    var modelName: String? = modelName
        private set
    var approvedBy: UUID? = approvedBy
        private set
    var approvedAt: Instant? = approvedAt
        private set
    var rejectedReason: String? = rejectedReason
        private set
    var rejectedAt: Instant? = rejectedAt
        private set
    var failureReason: String? = failureReason
        private set
    var aiRefused: Boolean = aiRefused
        private set
    var aiRefusalReason: String? = aiRefusalReason
        private set
    var updatedAt: Instant = updatedAt
        private set

    val weekEnd: LocalDate get() = weekStart.plusDays(6)
    val isAwaitingApproval: Boolean get() = status == CoachRunStatus.AWAITING_APPROVAL

    /** 같은 가족의 부모만, 승인 대기 상태에서만. 실패해도 상태는 그대로다. */
    fun approve(
        approver: CoachApprover,
        at: Instant,
    ) {
        authorize(approver)
        requireAwaitingApproval()
        status = CoachRunStatus.APPROVED
        approvedBy = approver.profileId
        approvedAt = at
        updatedAt = at
    }

    fun reject(
        approver: CoachApprover,
        reason: String?,
        at: Instant? = null,
    ) {
        authorize(approver)
        requireAwaitingApproval()
        status = CoachRunStatus.REJECTED
        rejectedReason = reason?.take(MAX_REASON)
        rejectedAt = at
        if (at != null) updatedAt = at
    }

    /** 승인된 실행의 제안만 미션 생성에 쓸 수 있다. */
    fun proposalsForMissionCreation(): List<CoachProposalItem> {
        if (status != CoachRunStatus.APPROVED) throw CoachApprovalRequiredException()
        return proposals
    }

    /** AI 가 run 을 받았다. 폴링 식별자를 기억해 둔다. */
    fun attachAiRun(
        aiRunId: String,
        at: Instant,
    ) {
        requireRunning()
        this.aiRunId = aiRunId
        updatedAt = at
    }

    /** AI 가 `succeeded` 를 돌려줬다. 변환된 제안을 붙이고 승인 대기로 옮긴다. */
    fun complete(
        steps: List<CoachStep>,
        proposals: List<CoachProposalItem>,
        proposalJson: String?,
        summary: String?,
        modelName: String?,
        at: Instant,
    ) {
        requireRunning()
        this.steps = steps
        this.proposals = proposals.sortedBy { it.position }
        this.proposalJson = proposalJson
        this.summary = summary
        this.modelName = modelName
        status = CoachRunStatus.AWAITING_APPROVAL
        updatedAt = at
    }

    /** AI 거부·실패·타임아웃·예외. 사유만 남기고 끝낸다. 같은 주에 새 실행을 다시 시작할 수 있다. */
    fun fail(
        reason: String,
        at: Instant,
        steps: List<CoachStep> = this.steps,
        refused: Boolean = false,
        refusalReason: String? = null,
    ) {
        requireRunning()
        this.steps = steps
        failureReason = reason.take(MAX_REASON)
        aiRefused = refused
        aiRefusalReason = refusalReason?.take(MAX_REFUSAL_REASON)
        status = CoachRunStatus.FAILED
        updatedAt = at
    }

    private fun authorize(approver: CoachApprover) {
        if (!approver.isParent) throw ParentRoleRequiredException()
        if (approver.familyId != familyId) throw CoachFamilyAccessDeniedException()
    }

    private fun requireAwaitingApproval() {
        if (status != CoachRunStatus.AWAITING_APPROVAL) throw CoachRunAlreadyDecidedException(status)
    }

    private fun requireRunning() {
        if (status != CoachRunStatus.RUNNING) throw CoachRunAlreadyDecidedException(status)
    }

    companion object {
        const val MAX_REASON = 300
        const val MAX_REFUSAL_REASON = 60
        const val POLL_AFTER_MS = 1500

        /** 요청 직후. AI 호출 전이며 커밋 후 비동기로 진행된다. */
        fun start(
            id: UUID,
            familyId: UUID,
            weekStart: LocalDate,
            triggerType: TriggerType,
            daysPerWeek: Int,
            minutesPerSession: Int,
            requestedBy: UUID?,
            at: Instant,
        ): CoachRun =
            CoachRun(
                id = id,
                familyId = familyId,
                weekStart = weekStart,
                triggerType = triggerType,
                daysPerWeek = daysPerWeek,
                minutesPerSession = minutesPerSession,
                requestedBy = requestedBy,
                createdAt = at,
                status = CoachRunStatus.RUNNING,
                proposals = emptyList(),
                steps = emptyList(),
                summary = null,
                proposalJson = null,
                aiRunId = null,
                modelName = null,
                approvedBy = null,
                approvedAt = null,
                rejectedReason = null,
                rejectedAt = null,
                failureReason = null,
                aiRefused = false,
                aiRefusalReason = null,
                updatedAt = at,
            )

        /** 제안이 준비된 승인 대기 실행. 도메인 테스트와 변환 결과 조립에 쓴다. */
        fun awaitingApproval(
            id: UUID,
            familyId: UUID,
            proposals: List<CoachProposalItem>,
            weekStart: LocalDate = LocalDate.EPOCH,
            steps: List<CoachStep> = emptyList(),
            summary: String? = null,
            daysPerWeek: Int = 3,
            minutesPerSession: Int = 15,
            at: Instant = Instant.EPOCH,
        ): CoachRun =
            CoachRun(
                id = id,
                familyId = familyId,
                weekStart = weekStart,
                triggerType = TriggerType.MANUAL,
                daysPerWeek = daysPerWeek,
                minutesPerSession = minutesPerSession,
                requestedBy = null,
                createdAt = at,
                status = CoachRunStatus.AWAITING_APPROVAL,
                proposals = proposals,
                steps = steps,
                summary = summary,
                proposalJson = null,
                aiRunId = null,
                modelName = null,
                approvedBy = null,
                approvedAt = null,
                rejectedReason = null,
                rejectedAt = null,
                failureReason = null,
                aiRefused = false,
                aiRefusalReason = null,
                updatedAt = at,
            )

        /** 저장소에서 복원. 규칙 검사 없이 상태를 그대로 싣는다. */
        fun reconstitute(
            id: UUID,
            familyId: UUID,
            weekStart: LocalDate,
            triggerType: TriggerType,
            daysPerWeek: Int,
            minutesPerSession: Int,
            requestedBy: UUID?,
            createdAt: Instant,
            status: CoachRunStatus,
            proposals: List<CoachProposalItem>,
            steps: List<CoachStep>,
            summary: String?,
            proposalJson: String?,
            aiRunId: String?,
            modelName: String?,
            approvedBy: UUID?,
            approvedAt: Instant?,
            rejectedReason: String?,
            rejectedAt: Instant?,
            failureReason: String?,
            aiRefused: Boolean,
            aiRefusalReason: String?,
            updatedAt: Instant,
        ): CoachRun =
            CoachRun(
                id,
                familyId,
                weekStart,
                triggerType,
                daysPerWeek,
                minutesPerSession,
                requestedBy,
                createdAt,
                status,
                proposals,
                steps,
                summary,
                proposalJson,
                aiRunId,
                modelName,
                approvedBy,
                approvedAt,
                rejectedReason,
                rejectedAt,
                failureReason,
                aiRefused,
                aiRefusalReason,
                updatedAt,
            )
    }
}
