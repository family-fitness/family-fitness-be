package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RecordStepsCommand(
    val profileId: UUID,
    val activityDate: LocalDate,
    val steps: Int,
)

data class StepsRecordedView(
    val source: ActivitySource,
    val serverVerified: Boolean,
    val verifiedBy: VerifiedBy,
    val missionProgress: Double,
    val missionCompleted: Boolean,
    val needsGuardianCheck: Boolean,
)

data class RecordTimerCommand(
    val profileId: UUID,
    val startedAt: Instant,
    val endedAt: Instant,
    val activeMinutes: Int,
)

data class TimerRecordedView(
    val activityDate: LocalDate,
    val source: ActivitySource,
    val serverVerified: Boolean,
    val totalActiveMinutes: Int,
    val missionProgress: Double,
    val missionCompleted: Boolean,
)

/**
 * 미션 경로의 활동 기록. 기록 자체는 activity 모듈([ActivityRecorder])이 하고, 이쪽은 참여자 진행도를 갱신한다.
 * 활동 날짜는 앱 시간대(KST) 기준이다.
 */
@Service
class MissionActivityService(
    private val missions: MissionRepository,
    private val familyAccess: FamilyAccess,
    private val recorder: ActivityRecorder,
    private val activityQuery: ActivityQuery,
    private val policy: MissionCompletionPolicy,
    private val time: AppTime,
) {
    /** 걸음수는 그날 총량으로 덮어쓴다(MANUAL). 도달해도 보호자 확인 전에는 완료가 아니다. */
    @Transactional
    fun recordSteps(
        userId: UUID,
        missionId: UUID,
        command: RecordStepsCommand,
    ): StepsRecordedView {
        require(!command.activityDate.isAfter(time.today())) { "activityDate 는 미래일 수 없습니다" }
        val mission = missions.findById(missionId) ?: throw MissionNotFoundException(missionId)
        familyAccess.requireMember(userId, mission.familyId)
        mission.participantOf(command.profileId)
        mission.requireMetric(TargetMetric.STEPS)

        recorder.overwriteSteps(command.profileId, command.activityDate, command.steps)
        val participant = policy.refreshParticipant(mission, command.profileId, time.now()).participantOf(command.profileId)
        return StepsRecordedView(
            source = ActivitySource.MANUAL,
            serverVerified = ActivitySource.MANUAL.serverVerified,
            verifiedBy = VerifiedBy.SELF_REPORT,
            missionProgress = participant.progress,
            missionCompleted = participant.completed,
            needsGuardianCheck = participant.needsGuardianCheck,
        )
    }

    /** 타이머 분 누적(TIMER). `endedAt-startedAt` 분을 넘으면 그 값으로 자른다(최소 1분). */
    @Transactional
    fun recordTimer(
        userId: UUID,
        missionId: UUID,
        command: RecordTimerCommand,
    ): TimerRecordedView {
        require(command.endedAt.isAfter(command.startedAt)) { "endedAt 은 startedAt 이후여야 합니다" }
        val mission = missions.findById(missionId) ?: throw MissionNotFoundException(missionId)
        familyAccess.requireMember(userId, mission.familyId)
        mission.participantOf(command.profileId)
        mission.requireMetric(TargetMetric.TIMER_MINUTES)

        val activityDate = time.dateOf(command.startedAt)
        val elapsed = Duration.between(command.startedAt, command.endedAt).toMinutes()
        val minutes = minOf(command.activeMinutes.toLong(), elapsed).coerceAtLeast(1).toInt()
        recorder.addActiveMinutes(command.profileId, activityDate, ActivitySource.TIMER, minutes)
        val participant = policy.refreshParticipant(mission, command.profileId, time.now()).participantOf(command.profileId)
        return TimerRecordedView(
            activityDate = activityDate,
            source = ActivitySource.TIMER,
            serverVerified = ActivitySource.TIMER.serverVerified,
            totalActiveMinutes = activityQuery.activeMinutesOn(command.profileId, activityDate),
            missionProgress = participant.progress,
            missionCompleted = participant.completed,
        )
    }
}
