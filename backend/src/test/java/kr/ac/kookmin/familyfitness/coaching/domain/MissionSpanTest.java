package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Test
    @DisplayName("캘린더에 싣는 날은 잡힌 날과 같은 규칙이고, 걸음수 미션도 싣는다(목 dayLogFor 는 지표로 거르지 않는다)")
    void 캘린더는_걸음수도_싣는다() {
        MissionSpan steps =
                new MissionSpan(today.minusDays(1), today.minusDays(1), TargetMetric.STEPS, false, Set.of());
        assertThat(steps.calendarDays(today)).containsExactly(today.minusDays(1));
        assertThat(steps.standingDays(today)).isEmpty();

        MissionSpan multi = new MissionSpan(
                today.minusDays(6), today.plusDays(1), TargetMetric.TIMER_MINUTES, true, Set.of(today.minusDays(4)));
        assertThat(multi.calendarDays(today)).containsExactlyElementsOf(multi.standingDays(today));
    }

    @Test
    @DisplayName("of — 칸 끝 기록이 없는 옛 미션을 완료했으면 완료 시각의 KST 날짜를 끝낸 날로 본다, 기록이 있으면 기록만")
    void of_는_옛_완료를_끝낸_날로_본다() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        // 2026-09-21 23:30 KST = 2026-09-21T14:30Z
        Instant verifiedAt = Instant.parse("2026-09-21T14:30:00Z");
        MissionSpan legacy = MissionSpan.of(
                today.minusDays(6),
                today.minusDays(1),
                TargetMetric.TIMER_MINUTES,
                true,
                true,
                verifiedAt,
                Set.of(),
                kst);
        assertThat(legacy.doneOn()).containsExactly(LocalDate.of(2026, 9, 21));
        assertThat(legacy.started()).isTrue();

        MissionSpan recorded = MissionSpan.of(
                today.minusDays(6),
                today.minusDays(1),
                TargetMetric.TIMER_MINUTES,
                true,
                true,
                verifiedAt,
                Set.of(today.minusDays(5)),
                kst);
        assertThat(recorded.doneOn()).containsExactly(today.minusDays(5));

        MissionSpan untouched = MissionSpan.of(
                today.minusDays(6), today.minusDays(1), TargetMetric.TIMER_MINUTES, false, false, null, Set.of(), kst);
        assertThat(untouched.started()).isFalse();
        assertThat(untouched.lastDoneOn()).isNull();
    }

    @Test
    @DisplayName("lastDoneOn — 칸을 끝낸 날 가운데 가장 늦은 날")
    void 마지막으로_끝낸_날() {
        MissionSpan span = new MissionSpan(
                today.minusDays(6),
                today,
                TargetMetric.TIMER_MINUTES,
                true,
                Set.of(today.minusDays(5), today.minusDays(2), today.minusDays(4)));
        assertThat(span.lastDoneOn()).isEqualTo(today.minusDays(2));
    }
}
