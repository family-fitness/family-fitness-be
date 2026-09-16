package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import org.jspecify.annotations.Nullable;

public record RejectCoachRunView(
        UUID coachRunId, CoachRunStatus status, @Nullable String rejectedReason, int missionCount) {}
