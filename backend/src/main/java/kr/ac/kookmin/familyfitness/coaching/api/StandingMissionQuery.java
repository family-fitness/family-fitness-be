package kr.ac.kookmin.familyfitness.coaching.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 오늘(KST) 서는 미션을 다른 모듈(알림 MISSION_READY)이 묻는 통로. coaching 이 구현한다.
 * 서는 날의 규칙은 잡힌 날(progress.api.PlannedDays)과 같다 — 걸음수(STEPS) 미션은 서지 않고, 하루짜리는 그날,
 * 여러 날짜리는 기간 안의 오늘 선다. 다 끝낸 참여자는 빼고, 남은 참여자가 없는 미션은 돌려주지 않는다. 권한은 부르는 쪽이 본다.
 */
public interface StandingMissionQuery {
    /** 이 가족에게 {@code today} 에 서는 미션. 차례는 정하지 않는다. */
    List<StandingMission> standingOn(UUID familyId, LocalDate today);

    /** 미션 하나가 {@code today} 에 서면 그 미션. 없는 미션 · 걸음수 · 기간 밖 · 모두 끝냈으면 null. */
    @Nullable
    StandingMission standing(UUID missionId, LocalDate today);
}
