package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import org.jspecify.annotations.Nullable;

/**
 * 직접 만들기 응답. 맨 위 칸은 첫 미션(여러 날이면 가장 이른 날) 것이다 — 한 건만 만들던 화면이 {@code missionId} 만 읽으므로 그대로 둔다.
 *
 * @param missions 이번 요청으로 생긴 미션 전부, 날짜 차례. 한 건만 만들어도 한 줄이 들어 있다
 */
public record MissionCreatedView(
        UUID missionId,
        MissionOrigin origin,
        @Nullable UUID coachRunId,
        boolean serverVerifiable,
        List<MissionPeriodView> missions) {

    static MissionCreatedView of(List<Mission> created) {
        Mission first = created.getFirst();
        return new MissionCreatedView(
                first.getId(),
                first.getOrigin(),
                first.getCoachRunId(),
                first.isServerVerifiable(),
                created.stream()
                        .map(it -> new MissionPeriodView(it.getId(), it.getStartsOn(), it.getEndsOn()))
                        .toList());
    }
}
