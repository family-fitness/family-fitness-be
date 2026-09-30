package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;

public record TimerRecordedView(
        LocalDate activityDate,
        ActivitySource source,
        boolean serverVerified,
        int totalActiveMinutes,
        double missionProgress,
        boolean missionCompleted) {}
