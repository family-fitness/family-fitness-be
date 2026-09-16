package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import org.jspecify.annotations.Nullable;

public record CreateMissionCommand(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TargetMetric targetMetric,
        int targetValue,
        @Nullable String videoId,
        List<UUID> participantProfileIds) {}
