package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record WeeklyReportView(
        LocalDate weekStart,
        LocalDate weekEnd,
        @Nullable String summary,
        MissionStatsView missionStats,
        List<MemberReportView> members,
        int cheerCount) {}
