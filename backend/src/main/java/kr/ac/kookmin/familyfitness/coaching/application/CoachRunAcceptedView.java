package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;

/** 202 응답. */
public record CoachRunAcceptedView(UUID coachRunId, CoachRunStatus status, int pollAfterMs) {}
