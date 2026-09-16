package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import org.jspecify.annotations.Nullable;

public record CoachRunView(
        UUID coachRunId,
        UUID familyId,
        CoachRunStatus status,
        LocalDate weekStart,
        @Nullable String summary,
        List<CoachStep> steps,
        @Nullable List<ProposalView> proposals,
        boolean canApprove,
        int missionCount,
        @Nullable String rejectedReason) {}
