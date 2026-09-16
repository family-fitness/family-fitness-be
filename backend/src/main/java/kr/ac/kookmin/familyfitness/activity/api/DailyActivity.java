package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;
import java.util.UUID;

public record DailyActivity(
        UUID profileId, LocalDate activityDate, ActivitySource source, int steps, int activeMinutes) {}
