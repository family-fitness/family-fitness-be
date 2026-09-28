package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionCompletions;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionProgress;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction;
import org.springframework.stereotype.Component;

/**
 * 미션 진행도 계산(서버 전용, 0.0~1.0). 판정 차례:
 * <ol>
 *   <li>칸 있는 미션: 그 사람이 끝낸 칸의 분 합 ÷ 전체 칸 분 합(FE 목 handlers.ts 칸 끝) → 칸을 전부 끝내면 완료,
 *       근거는 `VIDEO_PROGRESS`(결정 3-1). 활동 합계는 보지 않는다 — 어느 미션에서 한 활동인지 모르는 (프로필, 기간) 합계로
 *       세면, 한 미션만 해도 같은 기간의 다른 미션이 끝났다(Q-live-01 · Q-daily-09)
 *   <li>칸 없는 미션(걸음수 빼고)을 칸 끝으로 position 1 까지 끝냈으면: 미션 전체 한 칸을 끝낸 것이라 완료(결정 35)
 *   <li>그 밖의 칸 없는 옛 미션 — 지금 셈 그대로:
 *     <ul>
 *       <li>`VIDEO_DONE`: 미션 영상의 완주(maxProgress ≥ 0.9) 횟수 / targetValue → `VIDEO_PROGRESS`
 *       <li>`TIMER_MINUTES`: 기간 내 TIMER+VIDEO 분(verifiedMinutes) / targetValue → `TIMER`
 *       <li>`STEPS`: 기간 내 걸음 합 / targetValue → `SELF_REPORT`. 도달해도 보호자 확인 전에는 완료가 아니다
 *     </ul>
 * </ol>
 *
 * 저장 규칙: 칸 끝 · 활동 · 진행률 엔드포인트가 돌 때마다 해당 참여자 값을 다시 계산해 저장하고,
 * 목록 · 단건 · 주간 요약은 읽을 때 기간 안 미션의 미완료 참여자만 다시 계산해 바뀐 것만 저장한다(write-through).
 * 기간이 끝난 미션(endsOn &lt; 오늘)은 다시 계산하지 않고 마지막에 저장된 값을 그대로 쓴다.
 */
@Component
public class MissionCompletionPolicy {
    private final ActivityQuery activityQuery;
    private final VideoInteractionRepository interactions;
    private final MissionRepository missions;
    private final SessionCompletionRepository completions;

    public MissionCompletionPolicy(
            ActivityQuery activityQuery,
            VideoInteractionRepository interactions,
            MissionRepository missions,
            SessionCompletionRepository completions) {
        this.activityQuery = activityQuery;
        this.interactions = interactions;
        this.missions = missions;
        this.completions = completions;
    }

    /** 이 미션의 칸 끝 기록을 읽는다. */
    public MissionCompletions completionsOf(Mission mission) {
        return MissionCompletions.of(completions.findByMission(mission.getId()));
    }

    public MissionProgress compute(Mission mission, UUID profileId, MissionCompletions done) {
        if (countsBySessions(mission, profileId, done)) {
            return bySessions(mission, done.positionsOf(profileId));
        }
        return byActivity(mission, profileId);
    }

    /** 칸으로 세는가 — 칸 있는 미션은 늘, 칸 없는 미션은 걸음수가 아니고 미션 전체 한 칸(1)을 끝냈을 때. */
    private static boolean countsBySessions(Mission mission, UUID profileId, MissionCompletions done) {
        if (mission.hasSessions()) return true;
        return mission.getTargetMetric() != TargetMetric.STEPS && done.has(profileId, 1);
    }

    private static MissionProgress bySessions(Mission mission, List<Integer> donePositions) {
        int total = 0;
        int finished = 0;
        for (MissionSession session : mission.plannedSessions()) {
            total += session.minutes();
            if (donePositions.contains(session.position())) finished += session.minutes();
        }
        return MissionProgress.of(finished, total, donePositions.isEmpty() ? null : VerifiedBy.VIDEO_PROGRESS);
    }

    private MissionProgress byActivity(Mission mission, UUID profileId) {
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
                return MissionProgress.of(totals.steps(), mission.getTargetValue(), VerifiedBy.SELF_REPORT);
            }
        }
        throw new IllegalStateException("알 수 없는 지표: " + mission.getTargetMetric());
    }

    /** 한 참여자를 다시 계산해 반영하고 저장한다. */
    public Mission refreshParticipant(Mission mission, UUID profileId, Instant at) {
        return refreshParticipants(mission, List.of(profileId), at);
    }

    /** 여러 참여자를 다시 계산해 반영하고, 바뀐 것이 있으면 한 번 저장한다. 칸 끝이 끝낸 사람과 같이 한 보호자를 함께 셈한다. */
    public Mission refreshParticipants(Mission mission, Collection<UUID> profileIds, Instant at) {
        MissionCompletions done = completionsOf(mission);
        boolean changed = false;
        for (UUID profileId : profileIds) {
            boolean applied = mission.recordProgress(profileId, compute(mission, profileId, done), at);
            changed = applied || changed;
        }
        return changed ? missions.save(mission) : mission;
    }

    /** 읽기 경로의 다시 계산. 칸 끝 기록을 이 자리에서 읽는다. */
    public Mission refreshAll(Mission mission, LocalDate today, Instant at) {
        if (today.isAfter(mission.getEndsOn())) return mission;
        return refreshAll(mission, completionsOf(mission), today, at);
    }

    /**
     * 읽기 경로의 다시 계산. 기간이 끝난 미션(today 가 endsOn 뒤)은 건드리지 않는다 — 부를 때마다 지난 미션 수만큼
     * 활동 합계 쿼리가 늘던 것을 막는다. 기간 안이면 미완료 참여자만 다시 계산하고, 바뀐 것이 있을 때만 저장한다.
     *
     * @param done 이 미션의 칸 끝 기록(목록은 여러 미션 것을 한 번에 읽어 나눠 넘긴다)
     */
    public Mission refreshAll(Mission mission, MissionCompletions done, LocalDate today, Instant at) {
        if (today.isAfter(mission.getEndsOn())) return mission;
        boolean changed = false;
        for (MissionParticipant participant : mission.getParticipants()) {
            if (participant.isCompleted()) continue;
            boolean applied = mission.recordProgress(
                    participant.getProfileId(), compute(mission, participant.getProfileId(), done), at);
            changed = applied || changed;
        }
        return changed ? missions.save(mission) : mission;
    }
}
