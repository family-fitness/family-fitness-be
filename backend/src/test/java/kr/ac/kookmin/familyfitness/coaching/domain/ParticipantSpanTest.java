package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 리그의 잡힌 날 — 서는 날 가운데 미션을 만든 날(KST) 이후만(결정 41 · LG-V01). */
class ParticipantSpanTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 2026-09-24(목) */
    private final LocalDate today = LocalDate.of(2026, 9, 24);

    private final UUID kid = UUID.randomUUID();

    private ParticipantSpan span(LocalDate startsOn, LocalDate endsOn, Instant createdAt) {
        return new ParticipantSpan(
                kid, new MissionSpan(startsOn, endsOn, TargetMetric.TIMER_MINUTES, false, null), createdAt);
    }

    @Test
    @DisplayName("지난 날짜로 오늘 만든 하루짜리 미션은 잡힌 날이 아니다 — 분모를 늘리지 못한다")
    void 지난_날짜로_만든_하루짜리는_서지_않는다() {
        // 2026-09-24 10:00 KST 에 9/21 미션을 만들었다
        ParticipantSpan backdated =
                span(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), Instant.parse("2026-09-24T01:00:00Z"));

        assertThat(backdated.span().standingDays(today, KST)).containsExactly(LocalDate.of(2026, 9, 21));
        assertThat(backdated.standingDaysSinceCreated(today, KST)).isEmpty();
    }

    @Test
    @DisplayName("만든 날 그날 · 그 뒤의 날은 그대로 선다 — 만든 날은 KST 로 본다(UTC 로는 전날이어도)")
    void 만든_날_이후는_그대로() {
        // 2026-09-21 00:30 KST = 2026-09-20 15:30 UTC
        Instant createdAt = Instant.parse("2026-09-20T15:30:00Z");
        ParticipantSpan sameDay = span(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), createdAt);
        ParticipantSpan later = span(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 23), createdAt);

        assertThat(sameDay.standingDaysSinceCreated(today, KST)).containsExactly(LocalDate.of(2026, 9, 21));
        assertThat(later.standingDaysSinceCreated(today, KST)).containsExactly(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("주 중간에 승인한 주간 미션을 끝내 안 했으면 지난 마지막 날 하루로 선다 — 만든 날 뒤라 그대로 센다")
    void 주간_미션의_마지막_날() {
        // 월~일(9/14~9/20) 미션을 수요일(9/16)에 만들었다
        ParticipantSpan weekly =
                span(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), Instant.parse("2026-09-16T03:00:00Z"));

        assertThat(weekly.standingDaysSinceCreated(today, KST)).containsExactly(LocalDate.of(2026, 9, 20));
    }
}
