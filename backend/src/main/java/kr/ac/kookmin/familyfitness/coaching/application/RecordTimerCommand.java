package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.util.UUID;

public record RecordTimerCommand(UUID profileId, Instant startedAt, Instant endedAt, int activeMinutes) {}
