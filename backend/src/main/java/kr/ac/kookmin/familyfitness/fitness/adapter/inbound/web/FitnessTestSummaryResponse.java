package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import org.jspecify.annotations.Nullable;

// ---- GET /profiles/{profileId}/fitness-tests 의 한 행 ----
/**
 * 측정 이력 한 회차. overallPercentile 은 가족 체력 지도(latest.overallPercentile)와 같은 셈이다 — 규준이 붙은 항목이
 * 없으면 null. 키·몸무게는 그 회차에 같이 적은 값이고, 없으면 null. 호출 계정이 CHILD 면 몸무게는 늘 null 이다.
 */
public record FitnessTestSummaryResponse(
        UUID fitnessTestId,
        LocalDate testedOn,
        @Nullable Integer overallPercentile,
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg) {
    static FitnessTestSummaryResponse of(FitnessTest test, boolean parentScope) {
        return new FitnessTestSummaryResponse(
                test.getId(),
                test.getTestedOn(),
                test.getOverallPercentile(),
                test.getHeightCm(),
                parentScope ? test.getWeightKg() : null);
    }
}
