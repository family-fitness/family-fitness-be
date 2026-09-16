package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import kr.ac.kookmin.familyfitness.coaching.application.AppTime;

/** 테스트 기준 시각: 2026-09-09(수) 10:00 KST. 이번 주 월요일은 2026-09-07. */
public final class Fixed {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final Instant NOW = Instant.parse("2026-09-09T01:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    public static final LocalDate WEEK_START = LocalDate.of(2026, 9, 7);

    private Fixed() {}

    public static AppTime time() {
        return time(NOW);
    }

    public static AppTime time(Instant now) {
        return new AppTime(Clock.fixed(now, ZONE), ZONE);
    }
}
