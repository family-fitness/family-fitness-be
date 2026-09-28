package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운동이 서는 날(잡힌 날) — FE 목 standsOn(fe:src/mocks/history.ts:118-132) · FE 요청서 4장. */
class MissionSpanTest {
    /** 2026-09-24 */
    private final LocalDate today = LocalDate.of(2026, 9, 24);

    @Test
    @DisplayName("하루짜리는 그날 선다 — 했든 안 했든")
    void 하루짜리는_그날() {
        MissionSpan span =
                new MissionSpan(today.minusDays(2), today.minusDays(2), TargetMetric.TIMER_MINUTES, false, Set.of());
        assertThat(span.standingDays(today)).containsExactly(today.minusDays(2));
    }

    @Test
    @DisplayName("걸음수 미션은 서지 않는다 — 사람이 말한 값이라 움직인 날이 될 수 없다")
    void 걸음수_미션은_서지_않는다() {
        assertThat(new MissionSpan(today, today, TargetMetric.STEPS, false, Set.of()).standingDays(today))
                .isEmpty();
    }

    @Test
    @DisplayName("여러 날짜리 — 기간 안이면 오늘 선다, 한 번도 안 했어도 아직 기간이 남았으면 다른 날은 서지 않는다")
    void 여러_날짜리_하는_중() {
        MissionSpan span =
                new MissionSpan(today.minusDays(3), today.plusDays(3), TargetMetric.TIMER_MINUTES, false, Set.of());
        assertThat(span.standingDays(today)).containsExactly(today);
    }

    @Test
    @DisplayName("여러 날짜리 — 칸을 끝낸 날마다 선다(칸 끝 표의 completed_on). 기간 밖 날짜 · 오늘은 겹쳐 세지 않는다")
    void 여러_날짜리_칸을_끝낸_날마다() {
        MissionSpan span = new MissionSpan(
                today.minusDays(6),
                today.minusDays(1),
                TargetMetric.TIMER_MINUTES,
                true,
                Set.of(today.minusDays(5), today.minusDays(3), today.minusDays(9)));
        assertThat(span.standingDays(today)).containsExactly(today.minusDays(5), today.minusDays(3));

        MissionSpan doingToday = new MissionSpan(
                today.minusDays(2),
                today.plusDays(2),
                TargetMetric.TIMER_MINUTES,
                true,
                Set.of(today, today.minusDays(1)));
        assertThat(doingToday.standingDays(today)).containsExactly(today, today.minusDays(1));
    }

    @Test
    @DisplayName("여러 날짜리 — 끝내 한 칸도 안 했으면 지난 마지막 날 하루로 선다, 조금이라도 했으면 끝낸 날에만 선다")
    void 여러_날짜리_안_한_채로_지남() {
        MissionSpan missed =
                new MissionSpan(today.minusDays(6), today.minusDays(3), TargetMetric.TIMER_MINUTES, false, Set.of());
        assertThat(missed.standingDays(today)).containsExactly(today.minusDays(3));

        MissionSpan partly = new MissionSpan(
                today.minusDays(6), today.minusDays(3), TargetMetric.TIMER_MINUTES, true, Set.of(today.minusDays(5)));
        assertThat(partly.standingDays(today)).containsExactly(today.minusDays(5));
    }
}
