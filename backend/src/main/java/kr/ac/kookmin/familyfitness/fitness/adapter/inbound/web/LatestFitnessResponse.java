package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.application.LatestFitnessView;
import kr.ac.kookmin.familyfitness.fitness.domain.CoachDirection;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 이력이 없어도 200 — id·날짜·키·몸무게 null, 레이더 6요인 percentile null, 항목 빈 목록.
 * 키·몸무게는 그 회차에 같이 적은 값만 싣는다. 회차에 없으면 null 이고, 프로필 값으로 채우지 않는다.
 * 호출 계정이 CHILD 면 부모만 볼 값을 비운다 — 몸무게 · 레이더 백분위 · 항목의 백분위 · 등급 · 구간 · 「상위 n%」 ·
 * weakest · strongest · coachDirection 이 null 이다. 날짜 · 키 · 항목의 잰 값은 그대로 준다.
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
        @Nullable CoachDirection coachDirection,
        String disclaimer) {
    public static LatestFitnessResponse of(LatestFitnessView view) {
        FitnessTest test = view.test();
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
                    view.parentScope() ? CoachDirection.GROWTH : null,
                    Copy.FITNESS_DISCLAIMER);
        }
        if (!view.parentScope()) {
            return new LatestFitnessResponse(
                    test.getId(),
                    test.getTestedOn(),
                    test.getHeightCm(),
                    null,
                    FitnessFactor.RADAR.stream()
                            .map(it -> new RadarPointResponse(it, null))
                            .toList(),
                    ItemResult.valuesOnly(test),
                    null,
                    null,
                    null,
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
