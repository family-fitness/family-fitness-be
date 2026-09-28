package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.CoachDirection;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 이력이 없어도 200 — id·날짜·키·몸무게 null, 레이더 6요인 percentile null, 항목 빈 목록.
 * 키·몸무게는 그 회차에 같이 적은 값만 싣는다. 회차에 없으면 null 이고, 프로필 값으로 채우지 않는다.
 */
public record LatestFitnessResponse(
        @Nullable UUID fitnessTestId,
        @Nullable LocalDate testedOn,
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg,
        List<RadarPointResponse> radar,
        List<ItemResult> items,
        @Nullable FactorPoint weakest,
        @Nullable FactorPoint strongest,
        CoachDirection coachDirection,
        String disclaimer) {
    public static LatestFitnessResponse of(@Nullable FitnessTest test) {
        if (test == null) {
            return new LatestFitnessResponse(
                    null,
                    null,
                    null,
                    null,
                    FitnessFactor.RADAR.stream()
                            .map(it -> new RadarPointResponse(it, null))
                            .toList(),
                    List.of(),
                    null,
                    null,
                    CoachDirection.GROWTH,
                    Copy.FITNESS_DISCLAIMER);
        }
        return new LatestFitnessResponse(
                test.getId(),
                test.getTestedOn(),
                test.getHeightCm(),
                test.getWeightKg(),
                test.radar().stream()
                        .map(it -> new RadarPointResponse(it.factor(), it.percentile()))
                        .toList(),
                ItemResult.of(test),
                test.getWeakest(),
                test.getStrongest(),
                test.getCoachDirection(),
                Copy.FITNESS_DISCLAIMER);
    }
}
