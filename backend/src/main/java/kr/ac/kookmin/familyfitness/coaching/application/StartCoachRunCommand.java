package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record StartCoachRunCommand(@Nullable LocalDate weekStart, int daysPerWeek, int minutesPerSession) {
    public static final int DEFAULT_DAYS_PER_WEEK = 3;
    public static final int DEFAULT_MINUTES_PER_SESSION = 15;

    public StartCoachRunCommand(@Nullable LocalDate weekStart) {
        this(weekStart, DEFAULT_DAYS_PER_WEEK, DEFAULT_MINUTES_PER_SESSION);
    }
}
