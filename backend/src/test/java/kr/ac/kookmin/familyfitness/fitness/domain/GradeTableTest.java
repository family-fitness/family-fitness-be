package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 등급 기준표 한 줄 · 줄 고르기(AI `data/release/grade_thresholds.csv`, 한 줄의 뜻은 AI stats/tables.py Threshold.passes()).
 * 한 사람의 등급 판정은 CertifierTest · AiCertifyParityTest 가 본다.
 */
class GradeTableTest {
    private static GradeThreshold row(
            AgeGroup ageGroup,
            Sex sex,
            NormAgeUnit unit,
            int from,
            int to,
            Grade grade,
            String itemCode,
            String op,
            double cutoff,
            @Nullable Double upper) {
        return new GradeThreshold(
                ageGroup,
                sex,
                unit,
                from,
                to,
                grade,
                itemCode,
                ThresholdOp.of(op),
                BigDecimal.valueOf(cutoff),
                upper == null ? null : BigDecimal.valueOf(upper));
    }

    @Test
    @DisplayName("유아기 줄은 개월로 고른다 — 같은 만 4세라도 53개월과 54개월은 다른 줄")
    void 유아기_줄은_개월로_고른다() {
        GradeTable table = GradeTable.of(List.of(
                row(AgeGroup.TODDLER, Sex.M, NormAgeUnit.MONTHS, 48, 53, Grade.FIRST, "020", ">=", 48, null),
                row(AgeGroup.TODDLER, Sex.M, NormAgeUnit.MONTHS, 54, 59, Grade.FIRST, "020", ">=", 59, null)));
        assertThat(table.rows(AgeGroup.TODDLER, Sex.M, Grade.FIRST, 53))
                .singleElement()
                .extracting(GradeThreshold::cutoff)
                .isEqualTo(BigDecimal.valueOf(48.0));
        assertThat(table.rows(AgeGroup.TODDLER, Sex.M, Grade.FIRST, 54))
                .singleElement()
                .extracting(GradeThreshold::cutoff)
                .isEqualTo(BigDecimal.valueOf(59.0));
        assertThat(table.rows(AgeGroup.TODDLER, Sex.M, Grade.FIRST, 47)).isEmpty();
        assertThat(table.rows(AgeGroup.TODDLER, Sex.F, Grade.FIRST, 50)).isEmpty();
    }

    @Test
    @DisplayName("한 줄의 비교는 AI 와 같다 — >= · <= 는 경계 포함, < 는 경계 제외, between 은 양 끝 포함")
    void 한_줄의_비교는_AI_와_같다() {
        BigDecimal cutoff = new BigDecimal("23.3");
        assertThat(ThresholdOp.AT_LEAST.passes(new BigDecimal("23.3"), cutoff, null))
                .isTrue();
        assertThat(ThresholdOp.AT_LEAST.passes(new BigDecimal("23.29"), cutoff, null))
                .isFalse();
        assertThat(ThresholdOp.AT_MOST.passes(new BigDecimal("23.3"), cutoff, null))
                .isTrue();
        assertThat(ThresholdOp.AT_MOST.passes(new BigDecimal("23.31"), cutoff, null))
                .isFalse();
        assertThat(ThresholdOp.BELOW.passes(new BigDecimal("23.29"), cutoff, null))
                .isTrue();
        assertThat(ThresholdOp.BELOW.passes(new BigDecimal("23.3"), cutoff, null))
                .isFalse();
        BigDecimal low = new BigDecimal("18.5");
        BigDecimal high = new BigDecimal("25.0");
        assertThat(ThresholdOp.BETWEEN.passes(new BigDecimal("18.5"), low, high))
                .isTrue();
        assertThat(ThresholdOp.BETWEEN.passes(new BigDecimal("25"), low, high)).isTrue();
        assertThat(ThresholdOp.BETWEEN.passes(new BigDecimal("18.49"), low, high))
                .isFalse();
        assertThat(ThresholdOp.BETWEEN.passes(new BigDecimal("25.01"), low, high))
                .isFalse();
        assertThat(ThresholdOp.of("between")).isEqualTo(ThresholdOp.BETWEEN);
        assertThat(ThresholdOp.of("<")).isEqualTo(ThresholdOp.BELOW);
        assertThatThrownBy(() -> ThresholdOp.of("=")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("between 줄은 윗값이 있어야 하고, 같은 사람 · 등급 · 항목의 나이 구간이 겹치면 표를 만들지 않는다")
    void 기준표가_어긋나면_만들지_않는다() {
        assertThatThrownBy(() -> row(
                        AgeGroup.ADULT, Sex.M, NormAgeUnit.YEARS, 19, 24, Grade.THIRD, "018", "between", 18.5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> row(
                        AgeGroup.YOUTH, Sex.M, NormAgeUnit.YEARS, 11, 12, Grade.PARTICIPATION, "020", ">=", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        List<GradeThreshold> overlapping = List.of(
                row(AgeGroup.YOUTH, Sex.M, NormAgeUnit.YEARS, 11, 12, Grade.FIRST, "020", ">=", 77, null),
                row(AgeGroup.YOUTH, Sex.M, NormAgeUnit.YEARS, 12, 12, Grade.FIRST, "020", ">=", 91, null));
        assertThatThrownBy(() -> GradeTable.of(overlapping)).isInstanceOf(IllegalArgumentException.class);
        // 항목이 다르면 겹쳐도 된다
        GradeTable table = GradeTable.of(List.of(
                row(AgeGroup.YOUTH, Sex.M, NormAgeUnit.YEARS, 11, 11, Grade.FIRST, "020", ">=", 77, null),
                row(AgeGroup.YOUTH, Sex.M, NormAgeUnit.YEARS, 11, 11, Grade.FIRST, "028", ">=", 46.5, null)));
        assertThat(table.getSize()).isEqualTo(2);
    }
}
