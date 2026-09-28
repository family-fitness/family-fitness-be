package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import org.jspecify.annotations.Nullable;

/** profileId · date — 누구의 어느 날을 짠 실행인지. 옛 주간 실행은 둘 다 null. */
public record CoachRunView(
        UUID coachRunId,
        UUID familyId,
        CoachRunStatus status,
        LocalDate weekStart,
        @Nullable UUID profileId,
        @Nullable LocalDate date,
        @Nullable String summary,
        List<CoachStep> steps,
        @Nullable List<ProposalView> proposals,
        boolean canApprove,
        int missionCount,
        @Nullable String rejectedReason) {}
