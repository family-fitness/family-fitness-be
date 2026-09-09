package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.Mission
import kr.ac.kookmin.familyfitness.coaching.domain.MissionProgress
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * 미션 진행도 계산(서버 전용, 0.0~1.0).
 * - `VIDEO_DONE`: 미션 영상의 완주(maxProgress ≥ 0.9) 횟수 / targetValue → `VIDEO_PROGRESS`
 * - `TIMER_MINUTES`: 기간 내 TIMER+VIDEO 분(verifiedMinutes) / targetValue → `TIMER`
 * - `STEPS`: 기간 내 걸음 합 / targetValue — 도달해도 보호자 확인 전에는 완료가 아니다.
 *
 * 저장 규칙: 활동·진행률 엔드포인트가 돌 때마다 해당 참여자 값을 다시 계산해 저장하고,
 * 목록·주간 요약은 읽을 때 미완료 참여자를 다시 계산해 바뀐 것만 저장한다(write-through).
 */
@Component
class MissionCompletionPolicy(
    private val activityQuery: ActivityQuery,
    private val interactions: VideoInteractionRepository,
    private val missions: MissionRepository,
) {
    fun compute(
        mission: Mission,
        profileId: UUID,
    ): MissionProgress =
        when (mission.targetMetric) {
            TargetMetric.VIDEO_DONE -> {
                val videoId = mission.video?.videoId ?: return MissionProgress.NONE
                val done = interactions.find(profileId, videoId)?.completed == true
                MissionProgress.of(if (done) 1 else 0, mission.targetValue, VerifiedBy.VIDEO_PROGRESS)
            }

            TargetMetric.TIMER_MINUTES -> {
                val totals = activityQuery.totals(profileId, mission.startsOn, mission.endsOn)
                MissionProgress.of(totals.verifiedMinutes, mission.targetValue, VerifiedBy.TIMER)
            }

            TargetMetric.STEPS -> {
                val totals = activityQuery.totals(profileId, mission.startsOn, mission.endsOn)
                MissionProgress.of(totals.steps, mission.targetValue, null)
            }
        }

    /** 한 참여자를 다시 계산해 반영하고 저장한다. */
    fun refreshParticipant(
        mission: Mission,
        profileId: UUID,
        at: Instant,
    ): Mission {
        val changed = mission.recordProgress(profileId, compute(mission, profileId), at)
        return if (changed) missions.save(mission) else mission
    }

    /** 미완료 참여자 전원을 다시 계산한다. 바뀐 것이 있을 때만 저장한다. */
    fun refreshAll(
        mission: Mission,
        at: Instant,
    ): Mission {
        var changed = false
        mission.participants
            .filter { !it.completed }
            .forEach { changed = mission.recordProgress(it.profileId, compute(mission, it.profileId), at) || changed }
        return if (changed) missions.save(mission) else mission
    }
}
