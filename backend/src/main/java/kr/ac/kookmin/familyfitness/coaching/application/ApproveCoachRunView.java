package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;

public record ApproveCoachRunView(
        UUID coachRunId,
        CoachRunStatus status,
        UUID approvedBy,
        Instant approvedAt,
        List<CreatedMissionView> createdMissions) {}
