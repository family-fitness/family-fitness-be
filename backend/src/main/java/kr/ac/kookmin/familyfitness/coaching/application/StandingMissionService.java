package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMission;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMissionQuery;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 서는 미션({@link StandingMissionQuery} 구현). 알림 모듈이 MISSION_READY 를 만들 때 부른다.
 *
 * <p>서는지는 {@link MissionSpan#standingDays} 로 가른다(잡힌 날과 같은 규칙). 「오늘」 이 서는지는 그 사람의 진행(끝낸 칸 ·
 * 시작 여부)과 상관이 없다 — 여러 날짜리는 기간 안이면 오늘 서고, 끝낸 날 · 지난 마지막 날 규칙은 오늘이 아닌 날에만 쓰인다.
 * 그래서 진행 없이(시작 안 함 · 끝낸 날 없음) 만든 기간으로 오늘을 묻는다.
 */
@Service
@Transactional(readOnly = true)
public class StandingMissionService implements StandingMissionQuery {
    private final MissionRepository missions;

    public StandingMissionService(MissionRepository missions) {
        this.missions = missions;
    }

    @Override
    public List<StandingMission> standingOn(UUID familyId, LocalDate today) {
        List<StandingMission> out = new ArrayList<>();
        for (Mission mission : missions.findOverlapping(familyId, today, today)) {
            StandingMission standing = standingOf(mission, today);
            if (standing != null) out.add(standing);
        }
        return out;
    }

    @Override
    public @Nullable StandingMission standing(UUID missionId, LocalDate today) {
        Mission mission = missions.findById(missionId);
        return mission == null ? null : standingOf(mission, today);
    }

    private static @Nullable StandingMission standingOf(Mission mission, LocalDate today) {
        MissionSpan span =
                new MissionSpan(mission.getStartsOn(), mission.getEndsOn(), mission.getTargetMetric(), false, Set.of());
        if (!span.standingDays(today).contains(today)) return null;
        List<UUID> pending = mission.getParticipants().stream()
                .filter(it -> !it.isCompleted())
                .map(MissionParticipant::getProfileId)
                .toList();
        if (pending.isEmpty()) return null;
        return new StandingMission(
                mission.getId(), mission.getFamilyId(), mission.getTitle(), mission.getCreatedAt(), pending);
    }
}
