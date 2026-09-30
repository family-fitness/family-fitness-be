package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import org.jspecify.annotations.Nullable;

public record MissionView(
        UUID missionId,
        String title,
        MissionOrigin origin,
        @Nullable UUID coachRunId,
        TargetMetric targetMetric,
        int targetValue,
        boolean serverVerifiable,
        LocalDate startDate,
        LocalDate endDate,
        @Nullable String rationale,
        @Nullable MissionVideoView video,
        List<MissionParticipantView> participants,
        /** position 차례. 칸 없는 미션은 [] */
        List<MissionSessionView> sessions) {}
