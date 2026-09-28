package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionProgress;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction;
import org.springframework.stereotype.Component;

/**
 * 미션 진행도 계산(서버 전용, 0.0~1.0).
 * <ul>
 *   <li>`VIDEO_DONE`: 미션 영상의 완주(maxProgress ≥ 0.9) 횟수 / targetValue → `VIDEO_PROGRESS`
 *   <li>`TIMER_MINUTES`: 기간 내 TIMER+VIDEO 분(verifiedMinutes) / targetValue → `TIMER`
 *   <li>`STEPS`: 기간 내 걸음 합 / targetValue — 도달해도 보호자 확인 전에는 완료가 아니다.
 * </ul>
 *
 * 저장 규칙: 활동·진행률 엔드포인트가 돌 때마다 해당 참여자 값을 다시 계산해 저장하고,
 * 목록·단건·주간 요약은 읽을 때 기간 안 미션의 미완료 참여자만 다시 계산해 바뀐 것만 저장한다(write-through).
 * 기간이 끝난 미션(endsOn &lt; 오늘)은 다시 계산하지 않고 마지막에 저장된 값을 그대로 쓴다.
 */
@Component
public class MissionCompletionPolicy {
    private final ActivityQuery activityQuery;
    private final VideoInteractionRepository interactions;
    private final MissionRepository missions;

    public MissionCompletionPolicy(
            ActivityQuery activityQuery, VideoInteractionRepository interactions, MissionRepository missions) {
        this.activityQuery = activityQuery;
        this.interactions = interactions;
        this.missions = missions;
    }

    public MissionProgress compute(Mission mission, UUID profileId) {
        switch (mission.getTargetMetric()) {
            case VIDEO_DONE -> {
                MissionVideo video = mission.getVideo();
                if (video == null) return MissionProgress.NONE;
                VideoInteraction interaction = interactions.find(profileId, video.videoId());
                boolean done = interaction != null && interaction.isCompleted();
                return MissionProgress.of(done ? 1 : 0, mission.getTargetValue(), VerifiedBy.VIDEO_PROGRESS);
            }
            case TIMER_MINUTES -> {
                ActivityTotals totals = activityQuery.totals(profileId, mission.getStartsOn(), mission.getEndsOn());
                return MissionProgress.of(totals.verifiedMinutes(), mission.getTargetValue(), VerifiedBy.TIMER);
            }
            case STEPS -> {
                ActivityTotals totals = activityQuery.totals(profileId, mission.getStartsOn(), mission.getEndsOn());
                return MissionProgress.of(totals.steps(), mission.getTargetValue(), null);
            }
        }
        throw new IllegalStateException("알 수 없는 지표: " + mission.getTargetMetric());
    }

    /** 한 참여자를 다시 계산해 반영하고 저장한다. */
    public Mission refreshParticipant(Mission mission, UUID profileId, Instant at) {
        boolean changed = mission.recordProgress(profileId, compute(mission, profileId), at);
        return changed ? missions.save(mission) : mission;
    }

    /**
     * 읽기 경로의 다시 계산. 기간이 끝난 미션(today 가 endsOn 뒤)은 건드리지 않는다 — 부를 때마다 지난 미션 수만큼
     * 활동 합계 쿼리가 늘던 것을 막는다. 기간 안이면 미완료 참여자만 다시 계산하고, 바뀐 것이 있을 때만 저장한다.
     */
    public Mission refreshAll(Mission mission, LocalDate today, Instant at) {
        if (today.isAfter(mission.getEndsOn())) return mission;
        boolean changed = false;
        for (MissionParticipant participant : mission.getParticipants()) {
            if (participant.isCompleted()) continue;
            boolean applied = mission.recordProgress(
                    participant.getProfileId(), compute(mission, participant.getProfileId()), at);
            changed = applied || changed;
        }
        return changed ? missions.save(mission) : mission;
    }
}
