package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;

/** 커밋 후 {@link CoachRunExecutor} 가 받아 AI 편성을 진행한다. */
public record CoachRunRequested(UUID runId) {}
