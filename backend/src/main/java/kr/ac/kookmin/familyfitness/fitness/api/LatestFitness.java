package kr.ac.kookmin.familyfitness.fitness.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 코치 편성(assess 단계)이 읽는 최신 측정 요약. */
public record LatestFitness(
        UUID profileId,
        UUID fitnessTestId,
        LocalDate testedOn,
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg,
        /** itemCode → 원시 측정값. 005·006 은 애초에 저장되지 않는다. */
        Map<String, BigDecimal> measurements,
        @Nullable FactorPoint weakest,
        @Nullable FactorPoint strongest) {}
