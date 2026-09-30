package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.Sex;

/** `fitness_norms` 한 행. {@code value} 는 그 백분위에 해당하는 측정값(↓ 항목은 백분위가 오를수록 값이 작아진다). */
public record NormPoint(
        String itemCode,
        Sex sex,
        int ageFrom,
        int ageTo,
        int percentile,
        double value,
        int sourceYear,
        NormAgeUnit ageUnit) {
    public NormPoint(String itemCode, Sex sex, int ageFrom, int ageTo, int percentile, double value, int sourceYear) {
        this(itemCode, sex, ageFrom, ageTo, percentile, value, sourceYear, NormAgeUnit.YEARS);
    }
}
