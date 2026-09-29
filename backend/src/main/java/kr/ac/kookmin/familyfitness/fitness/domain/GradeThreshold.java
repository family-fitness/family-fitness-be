package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 국민체력100 등급 기준표(`fitness_grade_thresholds`) 한 줄 — (연령대, 성별, 나이 구간, 등급, 항목)의 공식 기준 하나.
 * 유아기 나이 구간은 개월(48~83), 나머지는 세다. {@code cutoffUpper} 는 {@link ThresholdOp#BETWEEN} 에서만 있다.
 */
public record GradeThreshold(
        AgeGroup ageGroup,
        Sex sex,
        NormAgeUnit ageUnit,
        int ageFrom,
        int ageTo,
        Grade grade,
        String itemCode,
        ThresholdOp op,
        BigDecimal cutoff,
        @Nullable BigDecimal cutoffUpper) {
    public GradeThreshold {
        if (grade == Grade.PARTICIPATION) throw new IllegalArgumentException("기준표에는 1 · 2 · 3등급만 있다");
        if (ageFrom > ageTo) throw new IllegalArgumentException("나이 구간이 뒤집혔다: " + ageFrom + "~" + ageTo);
        if ((op == ThresholdOp.BETWEEN) != (cutoffUpper != null)) {
            throw new IllegalArgumentException("between 만 윗값이 있다: " + op + " " + cutoffUpper);
        }
    }

    /** 이 줄의 나이 단위로 본 나이가 구간 안인가. 유아기 줄은 개월, 나머지는 세로 본다. */
    public boolean covers(int ageYears, int ageMonths) {
        int age = ageUnit == NormAgeUnit.MONTHS ? ageMonths : ageYears;
        return age >= ageFrom && age <= ageTo;
    }

    public boolean passes(BigDecimal value) {
        return op.passes(value, cutoff, cutoffUpper);
    }
}
