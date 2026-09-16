package kr.ac.kookmin.familyfitness.fitness.api;

import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface FitnessQuery {
    @Nullable
    LatestFitness latestOf(UUID profileId);

    /** 가족 중 측정 기록이 한 명이라도 있는가 (`NO_MEASURED_MEMBER` 판정). */
    boolean hasAnyTest(Collection<UUID> profileIds);
}
