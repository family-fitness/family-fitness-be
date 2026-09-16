package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record VideoProgressCommand(
        UUID profileId,
        double progress,
        int watchedSec,
        @Nullable UUID missionId) {}
