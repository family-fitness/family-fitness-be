package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record PredictionPoint(
        PredictionScenario scenario,
        String itemCode,
        int yearsFromNow,
        @Nullable BigDecimal p10,
        @Nullable BigDecimal p50,
        @Nullable BigDecimal p90) {}
