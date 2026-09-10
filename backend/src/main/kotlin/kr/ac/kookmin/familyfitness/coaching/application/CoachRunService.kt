package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.AlreadyRunThisWeekException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachApprover
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunAlreadyDecidedException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunInProgressException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.Mission
import kr.ac.kookmin.familyfitness.coaching.domain.NoMeasuredMemberException
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 커밋 후 [CoachRunExecutor] 가 받아 AI 편성을 진행한다. */
data class CoachRunRequested(
    val runId: UUID,
)

/**
 * 코치 실행 유스케이스: 시작(202) · 조회 · 승인 · 거절.
 * 미션은 승인 트랜잭션 안에서만 만들어진다(직접 만들기 제외).
 */
@Service
class CoachRunService(
    private val runs: CoachRunRepository,
    private val missions: MissionRepository,
    private val videos: ExerciseVideoRepository,
    private val familyAccess: FamilyAccess,
    private val profileQuery: ProfileQuery,
    private val fitnessQuery: FitnessQuery,
    private val events: ApplicationEventPublisher,
    private val time: AppTime,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * RUNNING 으로 저장하고 이벤트만 발행한다. AI 호출은 커밋 후 비동기.
     * ▲ 같은 가족의 동시 요청은 존재 검사 사이의 경합이 가능하다(부분 유니크 인덱스를 못 쓰는 H2 호환 스키마).
     */
    @Transactional
    fun start(
        userId: UUID,
        familyId: UUID,
        command: StartCoachRunCommand,
    ): CoachRunAcceptedView {
        val caller = familyAccess.requireMember(userId, familyId)
        val weekStart = command.weekStart?.let(AppTime::weekStartOf) ?: time.thisWeekStart()

        if (runs.existsByFamilyAndStatus(familyId, CoachRunStatus.RUNNING)) throw CoachRunInProgressException(familyId)
        if (runs.existsByFamilyAndWeekAndStatusIn(familyId, weekStart, DECIDED_OR_AWAITING)) throw AlreadyRunThisWeekException(weekStart)
        val memberIds = profileQuery.summariesOfFamily(familyId).map { it.profileId }
        if (!fitnessQuery.hasAnyTest(memberIds)) throw NoMeasuredMemberException(familyId)

        val run =
            CoachRun.start(
                id = UUID.randomUUID(),
                familyId = familyId,
                weekStart = weekStart,
                triggerType = TriggerType.MANUAL,
                daysPerWeek = command.daysPerWeek,
                minutesPerSession = command.minutesPerSession,
                requestedBy = caller.profileId,
                at = time.now(),
            )
        runs.save(run)
        events.publishEvent(CoachRunRequested(run.id))
        return CoachRunAcceptedView(run.id, run.status, CoachRun.POLL_AFTER_MS)
    }

    /**
     * 일요일 20:00 스케줄(보드 F3 `trigger = SCHEDULE`). 사람이 아무것도 안 해도 주 1회 제안이 만들어진다.
     * 실행 중·이번 주 결정된 run 이 있거나 측정된 구성원이 없으면 조용히 건너뛴다(예외가 아니다).
     */
    @Transactional
    fun startScheduled(familyId: UUID): UUID? {
        val weekStart = time.thisWeekStart()
        if (runs.existsByFamilyAndStatus(familyId, CoachRunStatus.RUNNING)) return null
        if (runs.existsByFamilyAndWeekAndStatusIn(familyId, weekStart, DECIDED_OR_AWAITING)) return null
        val memberIds = profileQuery.summariesOfFamily(familyId).map { it.profileId }
        if (memberIds.isEmpty() || !fitnessQuery.hasAnyTest(memberIds)) return null

        val run =
            CoachRun.start(
                id = UUID.randomUUID(),
                familyId = familyId,
                weekStart = weekStart,
                triggerType = TriggerType.SCHEDULE,
                daysPerWeek = StartCoachRunCommand.DEFAULT_DAYS_PER_WEEK,
                minutesPerSession = StartCoachRunCommand.DEFAULT_MINUTES_PER_SESSION,
                requestedBy = null,
                at = time.now(),
            )
        runs.save(run)
        events.publishEvent(CoachRunRequested(run.id))
        return run.id
    }

    @Transactional(readOnly = true)
    fun get(
        userId: UUID,
        runId: UUID,
    ): CoachRunView {
        val run = runs.findById(runId) ?: throw CoachRunNotFoundException(runId)
        val caller = familyAccess.requireMember(userId, run.familyId)
        return toView(run, canApprove = run.isAwaitingApproval && caller.isParent)
    }

    /**
     * 한 트랜잭션: 도메인 승인 → 조건부 UPDATE(0행이면 409) → 제안 복사로 미션·참여자 INSERT.
     * 승인자는 요청 값이 아니라 [FamilyAccess.requireParent] 가 돌려준 부모 프로필이다.
     */
    @Transactional
    fun approve(
        userId: UUID,
        runId: UUID,
    ): ApproveCoachRunView {
        val run = runs.findById(runId) ?: throw CoachRunNotFoundException(runId)
        val parent = familyAccess.requireParent(userId, run.familyId)
        val now = time.now()
        run.approve(CoachApprover(parent.profileId, parent.familyId, parent.isParent), now)
        if (!runs.approveIfAwaiting(run)) throw CoachRunAlreadyDecidedException(runs.currentStatus(runId) ?: run.status)

        val created =
            run.proposalsForMissionCreation().mapNotNull { item ->
                if (item.participants.isEmpty()) {
                    log.warn("참여자 없는 제안 항목은 미션으로 만들지 않는다: run={} position={}", run.id, item.position)
                    return@mapNotNull null
                }
                val mission = missions.save(Mission.fromProposal(UUID.randomUUID(), run, item, parent.profileId, now))
                CreatedMissionView(mission.id, mission.title, mission.origin)
            }
        return ApproveCoachRunView(run.id, run.status, checkNotNull(run.approvedBy), checkNotNull(run.approvedAt), created)
    }

    @Transactional
    fun reject(
        userId: UUID,
        runId: UUID,
        reason: String?,
    ): RejectCoachRunView {
        val run = runs.findById(runId) ?: throw CoachRunNotFoundException(runId)
        val parent = familyAccess.requireParent(userId, run.familyId)
        run.reject(CoachApprover(parent.profileId, parent.familyId, parent.isParent), reason, time.now())
        if (!runs.rejectIfAwaiting(run)) throw CoachRunAlreadyDecidedException(runs.currentStatus(runId) ?: run.status)
        return RejectCoachRunView(run.id, run.status, run.rejectedReason, missionCount = 0)
    }

    private fun toView(
        run: CoachRun,
        canApprove: Boolean,
    ): CoachRunView {
        val videoIds = run.proposals.mapNotNull { it.video?.videoId }.toSet()
        val videoById = if (videoIds.isEmpty()) emptyMap() else videos.findAllByIds(videoIds).associateBy { it.videoId }
        val proposals =
            run.proposals
                .map { item ->
                    ProposalView(
                        position = item.position,
                        title = item.title,
                        rationale = item.rationale,
                        targetMetric = item.targetMetric,
                        targetValue = item.targetValue,
                        startDate = item.startsOn ?: run.weekStart,
                        endDate = item.endsOn ?: run.weekEnd,
                        participants = item.participants.map { ProposalParticipantView(it.profileId, it.role, it.coachRole) },
                        video =
                            item.video?.let { v ->
                                val video = videoById[v.videoId]
                                ProposalVideoView(
                                    videoId = v.videoId,
                                    title = video?.title,
                                    url = video?.url ?: "https://www.youtube.com/watch?v=${v.videoId}",
                                    startSec = v.startSec,
                                    badges = video?.badges ?: emptyList(),
                                )
                            },
                        citations = item.citations.map { ProposalCitationView(it.index, it.label, it.chunkId, it.url) },
                    )
                }.takeIf { it.isNotEmpty() }
        return CoachRunView(
            coachRunId = run.id,
            familyId = run.familyId,
            status = run.status,
            weekStart = run.weekStart,
            summary = run.summary,
            steps = run.steps,
            proposals = proposals,
            canApprove = canApprove,
            missionCount = missions.countByCoachRun(run.id),
            rejectedReason = run.rejectedReason,
        )
    }

    companion object {
        val DECIDED_OR_AWAITING = setOf(CoachRunStatus.AWAITING_APPROVAL, CoachRunStatus.APPROVED)
    }
}
