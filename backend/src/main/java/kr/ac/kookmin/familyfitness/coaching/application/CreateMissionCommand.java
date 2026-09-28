package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import org.jspecify.annotations.Nullable;

/** @param sessions 칸. 비어 있으면 칸 없는 미션이다. 있으면 {@code targetValue} 가 칸 시간의 합과 같아야 한다. */
public record CreateMissionCommand(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TargetMetric targetMetric,
        int targetValue,
        @Nullable String videoId,
        List<UUID> participantProfileIds,
        List<MissionSession> sessions) {

    /** 칸 없는 미션. */
    public CreateMissionCommand(
            String title,
            LocalDate startDate,
            LocalDate endDate,
            TargetMetric targetMetric,
            int targetValue,
            @Nullable String videoId,
            List<UUID> participantProfileIds) {
        this(title, startDate, endDate, targetMetric, targetValue, videoId, participantProfileIds, List.of());
    }
}
