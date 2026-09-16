package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.CoachDirection;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/** 이력이 없어도 200 — id·날짜 null, 레이더 5요인 percentile null, 항목 빈 목록. */
public record LatestFitnessResponse(
        @Nullable UUID fitnessTestId,
        @Nullable LocalDate testedOn,
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
