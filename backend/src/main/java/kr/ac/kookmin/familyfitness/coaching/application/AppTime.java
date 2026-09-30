package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

/** 모든 「지금」은 주입된 {@link Clock} 으로, 날짜는 앱 시간대(KST)로 계산한다. */
@Component
public class AppTime {
    private final Clock clock;
    private final ZoneId zone;

    public AppTime(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public ZoneId getZone() {
        return zone;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(now(), zone);
    }

    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** 이번 주 월요일. */
    public LocalDate thisWeekStart() {
        return weekStartOf(today());
    }

    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
