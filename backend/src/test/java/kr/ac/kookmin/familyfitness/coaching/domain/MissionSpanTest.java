package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운동이 서는 날(잡힌 날) — FE 목 standsOn(fe:src/mocks/history.ts:118-132) · FE 요청서 4장. */
class MissionSpanTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 2026-09-24 */
    private final LocalDate today = LocalDate.of(2026, 9, 24);

    @Test
    @DisplayName("하루짜리는 그날 선다 — 했든 안 했든")
    void 하루짜리는_그날() {
        MissionSpan span =
                new MissionSpan(today.minusDays(2), today.minusDays(2), TargetMetric.TIMER_MINUTES, false, null);
        assertThat(span.standingDays(today, KST)).containsExactly(today.minusDays(2));
    }

    @Test
    @DisplayName("걸음수 미션은 서지 않는다 — 사람이 말한 값이라 움직인 날이 될 수 없다")
    void 걸음수_미션은_서지_않는다() {
        assertThat(new MissionSpan(today, today, TargetMetric.STEPS, false, null).standingDays(today, KST))
                .isEmpty();
    }

    @Test
    @DisplayName("여러 날짜리 — 기간 안이면 오늘 선다, 한 번도 안 했어도 아직 기간이 남았으면 다른 날은 서지 않는다")
    void 여러_날짜리_하는_중() {
        MissionSpan span =
                new MissionSpan(today.minusDays(3), today.plusDays(3), TargetMetric.TIMER_MINUTES, false, null);
        assertThat(span.standingDays(today, KST)).containsExactly(today);
    }

    @Test
    @DisplayName("여러 날짜리 — 끝낸 날(KST)에 선다")
    void 여러_날짜리_끝낸_날() {
        // 2026-09-21 23:30 KST = 14:30 UTC
        Instant completedAt = Instant.parse("2026-09-21T14:30:00Z");
        MissionSpan span =
                new MissionSpan(today.minusDays(6), today.minusDays(1), TargetMetric.TIMER_MINUTES, true, completedAt);
        assertThat(span.standingDays(today, KST)).containsExactly(LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("여러 날짜리 — 끝내 한 번도 안 했으면 지난 마지막 날 하루로 선다, 조금이라도 했으면 서지 않는다")
    void 여러_날짜리_안_한_채로_지남() {
        MissionSpan missed =
                new MissionSpan(today.minusDays(6), today.minusDays(3), TargetMetric.TIMER_MINUTES, false, null);
        assertThat(missed.standingDays(today, KST)).containsExactly(today.minusDays(3));

        MissionSpan partly =
                new MissionSpan(today.minusDays(6), today.minusDays(3), TargetMetric.TIMER_MINUTES, true, null);
        assertThat(partly.standingDays(today, KST)).isEmpty();
    }
}
