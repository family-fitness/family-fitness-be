package kr.ac.kookmin.familyfitness.coaching.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class MissionOrigin {
    COACH,
    MANUAL,
}

enum class VerifiedBy {
    VIDEO_PROGRESS,
    TIMER,
    SELF_REPORT,
}

enum class ParticipantStatus {
    PENDING,
    COMPLETED,
}

/** 목록 필터. `ACTIVE` = 오늘 ≤ endDate 이고 전원 완료 아님 · `DONE` = 전원 완료 · `EXPIRED` = endDate 지났고 미완료. */
enum class MissionStatus {
    ACTIVE,
    DONE,
    EXPIRED,
}

data class MissionVideo(
    val videoId: String,
    val startSec: Int?,
)

/** 서버가 계산한 진행도와 그 근거. 정책([kr.ac.kookmin.familyfitness.coaching.application.MissionCompletionPolicy])이 만든다. */
data class MissionProgress(
    val progress: Double,
    val verifiedBy: VerifiedBy?,
) {
    init {
        require(progress in 0.0..1.0) { "진행도는 0~1 이어야 한다: $progress" }
    }

    companion object {
        val NONE = MissionProgress(0.0, null)

        fun of(
            achieved: Number,
            target: Int,
            verifiedBy: VerifiedBy?,
        ): MissionProgress {
            val ratio = if (target <= 0) 0.0 else achieved.toDouble() / target
            return MissionProgress(ratio.coerceIn(0.0, 1.0), verifiedBy)
        }
    }
}

/**
 * 미션 참여자 한 명의 진행 상태. 진행도는 서버만 계산한다(0.0~1.0).
 * 서버가 검증할 수 있는 지표는 목표 도달 즉시 완료, `STEPS` 는 보호자 확인([confirm])이 있어야 완료다.
 */
class MissionParticipant private constructor(
    val profileId: UUID,
    val coachRole: String?,
    progress: Double,
    status: ParticipantStatus,
    verifiedBy: VerifiedBy?,
    verifiedAt: Instant?,
    confirmedBy: UUID?,
    updatedAt: Instant,
) {
    var progress: Double = progress
        private set
    var status: ParticipantStatus = status
        private set
    var verifiedBy: VerifiedBy? = verifiedBy
        private set
    var verifiedAt: Instant? = verifiedAt
        private set
    var confirmedBy: UUID? = confirmedBy
        private set
    var updatedAt: Instant = updatedAt
        private set

    val completed: Boolean get() = status == ParticipantStatus.COMPLETED
    val reachedTarget: Boolean get() = progress >= 1.0

    /** 목표에는 도달했지만 사람이 말한 값이라 보호자 확인이 남았다. */
    val needsGuardianCheck: Boolean get() = reachedTarget && !completed

    /** 새 진행도를 반영한다. 완료된 참여자는 진행도를 되돌리지 않는다. 바뀐 것이 있으면 true. */
    internal fun apply(
        computed: MissionProgress,
        serverVerifiable: Boolean,
        at: Instant,
    ): Boolean {
        if (completed) return false
        var changed = false
        if (computed.progress != progress) {
            progress = computed.progress
            changed = true
        }
        if (serverVerifiable && reachedTarget) {
            status = ParticipantStatus.COMPLETED
            verifiedBy = computed.verifiedBy
            verifiedAt = at
            changed = true
        }
        if (changed) updatedAt = at
        return changed
    }

    /** 보호자 확인. 목표 도달 전에는 [TargetNotReachedException]. 이미 완료면 그대로 둔다. */
    internal fun confirm(
        by: UUID,
        at: Instant,
    ) {
        if (completed) return
        if (!reachedTarget) throw TargetNotReachedException(progress)
        status = ParticipantStatus.COMPLETED
        verifiedBy = VerifiedBy.SELF_REPORT
        verifiedAt = at
        confirmedBy = by
        updatedAt = at
    }

    companion object {
        fun pending(
            profileId: UUID,
            coachRole: String?,
            at: Instant,
        ): MissionParticipant = MissionParticipant(profileId, coachRole, 0.0, ParticipantStatus.PENDING, null, null, null, at)

        fun reconstitute(
            profileId: UUID,
            coachRole: String?,
            progress: Double,
            status: ParticipantStatus,
            verifiedBy: VerifiedBy?,
            verifiedAt: Instant?,
            confirmedBy: UUID?,
            updatedAt: Instant,
        ): MissionParticipant = MissionParticipant(profileId, coachRole, progress, status, verifiedBy, verifiedAt, confirmedBy, updatedAt)
    }
}

/**
 * 부모가 직접 만들었거나 승인된 제안에서 만들어진 가족의 실행 과제.
 * 승인([CoachRun.approve])과 직접 만들기 외에는 미션이 생기지 않는다.
 */
class Mission private constructor(
    val id: UUID,
    val familyId: UUID,
    val coachRunId: UUID?,
    val title: String,
    val description: String?,
    val origin: MissionOrigin,
    val targetMetric: TargetMetric,
    val targetValue: Int,
    val video: MissionVideo?,
    val rationale: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val createdBy: UUID?,
    val createdAt: Instant,
    participants: List<MissionParticipant>,
) {
    init {
        require(targetValue > 0) { "목표값은 0보다 커야 한다" }
        require(!endsOn.isBefore(startsOn)) { "endDate 는 startDate 이후여야 한다" }
        require(participants.isNotEmpty()) { "참여자가 한 명 이상 있어야 한다" }
        require((origin == MissionOrigin.COACH) == (coachRunId != null)) { "COACH 미션만 coachRunId 를 가진다" }
    }

    val participants: List<MissionParticipant> = participants.toList()
    val serverVerifiable: Boolean get() = targetMetric.serverVerifiable
    val allCompleted: Boolean get() = participants.all { it.completed }

    fun participantOf(profileId: UUID): MissionParticipant =
        participants.firstOrNull { it.profileId == profileId } ?: throw NotParticipantException(id, profileId)

    fun isParticipant(profileId: UUID): Boolean = participants.any { it.profileId == profileId }

    fun requireMetric(expected: TargetMetric) {
        if (targetMetric != expected) throw InvalidMetricException(expected, targetMetric)
    }

    /** 정책이 계산한 진행도를 참여자에게 반영한다. 서버 검증 지표는 도달 즉시 완료된다. */
    fun recordProgress(
        profileId: UUID,
        computed: MissionProgress,
        at: Instant,
    ): Boolean = participantOf(profileId).apply(computed, serverVerifiable, at)

    fun confirm(
        profileId: UUID,
        confirmedBy: UUID,
        at: Instant,
    ): MissionParticipant = participantOf(profileId).also { it.confirm(confirmedBy, at) }

    fun statusOn(today: LocalDate): MissionStatus =
        when {
            allCompleted -> MissionStatus.DONE
            today.isAfter(endsOn) -> MissionStatus.EXPIRED
            else -> MissionStatus.ACTIVE
        }

    fun overlaps(
        from: LocalDate,
        to: LocalDate,
    ): Boolean = !startsOn.isAfter(to) && !endsOn.isBefore(from)

    companion object {
        fun manual(
            id: UUID,
            familyId: UUID,
            title: String,
            targetMetric: TargetMetric,
            targetValue: Int,
            video: MissionVideo?,
            startsOn: LocalDate,
            endsOn: LocalDate,
            participantProfileIds: List<UUID>,
            createdBy: UUID,
            at: Instant,
        ): Mission =
            Mission(
                id = id,
                familyId = familyId,
                coachRunId = null,
                title = title,
                description = null,
                origin = MissionOrigin.MANUAL,
                targetMetric = targetMetric,
                targetValue = targetValue,
                video = video,
                rationale = null,
                startsOn = startsOn,
                endsOn = endsOn,
                createdBy = createdBy,
                createdAt = at,
                participants = participantProfileIds.distinct().map { MissionParticipant.pending(it, null, at) },
            )

        /** 승인된 제안 항목의 복사. 제안에 기간이 없으면 실행의 주(월~일)를 쓴다. */
        fun fromProposal(
            id: UUID,
            run: CoachRun,
            item: CoachProposalItem,
            createdBy: UUID,
            at: Instant,
        ): Mission =
            Mission(
                id = id,
                familyId = run.familyId,
                coachRunId = run.id,
                title = item.title,
                description = item.description,
                origin = MissionOrigin.COACH,
                targetMetric = TargetMetric.valueOf(item.targetMetric),
                targetValue = item.targetValue,
                video = item.video?.let { MissionVideo(it.videoId, it.startSec) },
                rationale = item.rationale,
                startsOn = item.startsOn ?: run.weekStart,
                endsOn = item.endsOn ?: run.weekEnd,
                createdBy = createdBy,
                createdAt = at,
                participants =
                    item.participants
                        .distinctBy { it.profileId }
                        .map { MissionParticipant.pending(it.profileId, it.coachRole, at) },
            )

        fun reconstitute(
            id: UUID,
            familyId: UUID,
            coachRunId: UUID?,
            title: String,
            description: String?,
            origin: MissionOrigin,
            targetMetric: TargetMetric,
            targetValue: Int,
            video: MissionVideo?,
            rationale: String?,
            startsOn: LocalDate,
            endsOn: LocalDate,
            createdBy: UUID?,
            createdAt: Instant,
            participants: List<MissionParticipant>,
        ): Mission =
            Mission(
                id,
                familyId,
                coachRunId,
                title,
                description,
                origin,
                targetMetric,
                targetValue,
                video,
                rationale,
                startsOn,
                endsOn,
                createdBy,
                createdAt,
                participants,
            )
    }
}
