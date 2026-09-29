package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 항목 등급 = 국민체력100 공식 기준표(AI `data/release/grade_thresholds.csv`, 한 줄의 뜻은 AI stats/tables.py
 * Threshold.passes()). 아래 기준값은 그 CSV 에서 그대로 옮겼다.
 */
class GradeTableTest {
    private static final List<GradeThreshold> OFFICIAL = new ArrayList<>();

    static {
        // 유소년 M 11세
        years(AgeGroup.YOUTH, Sex.M, 11, "020", ">=", 77.0, 59.0, 46.0);
        years(AgeGroup.YOUTH, Sex.M, 11, "028", ">=", 46.5, 40.6, 35.4);
        years(AgeGroup.YOUTH, Sex.M, 11, "009", ">=", 36.0, 26.0, 19.0);
        years(AgeGroup.YOUTH, Sex.M, 11, "012", ">=", 11.5, 7.8, 4.0);
        years(AgeGroup.YOUTH, Sex.M, 11, "043", ">=", 33.0, 30.0, null); // 3등급 줄 없음
        years(AgeGroup.YOUTH, Sex.M, 11, "022", ">=", 161.0, 147.0, null); // 3등급 줄 없음
        // 청소년 F 15세 — 낮을수록 좋은 항목은 <=
        years(AgeGroup.ADOLESCENT, Sex.F, 15, "013", "<=", 21.4, 22.7, null);
        years(AgeGroup.ADOLESCENT, Sex.F, 15, "017", "<=", 52.3, 56.8, null);
        // 유아기 M — 나이 구간이 개월(48~53 · 54~59)
        months(Sex.M, 48, 53, "020", ">=", 48.0, 35.0, 25.0);
        months(Sex.M, 54, 59, "020", ">=", 59.0, 44.0, 34.0);
        months(Sex.M, 48, 53, "050", "<=", 11.06, 11.73, null);
        months(Sex.M, 54, 59, "050", "<=", 9.88, 10.73, null);
    }

    private static void years(
            AgeGroup ageGroup,
            Sex sex,
            int age,
            String itemCode,
            String op,
            double first,
            double second,
            @Nullable Double third) {
        add(ageGroup, sex, NormAgeUnit.YEARS, age, age, itemCode, op, first, second, third);
    }

    private static void months(
            Sex sex,
            int from,
            int to,
            String itemCode,
            String op,
            double first,
            double second,
            @Nullable Double third) {
        add(AgeGroup.TODDLER, sex, NormAgeUnit.MONTHS, from, to, itemCode, op, first, second, third);
    }

    private static void add(
            AgeGroup ageGroup,
            Sex sex,
            NormAgeUnit unit,
            int from,
            int to,
            String itemCode,
            String op,
            double first,
            double second,
            @Nullable Double third) {
        OFFICIAL.add(row(ageGroup, sex, unit, from, to, Grade.FIRST, itemCode, op, first, null));
        OFFICIAL.add(row(ageGroup, sex, unit, from, to, Grade.SECOND, itemCode, op, second, null));
        if (third != null) OFFICIAL.add(row(ageGroup, sex, unit, from, to, Grade.THIRD, itemCode, op, third, null));
    }

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

    private final GradeTable table = GradeTable.of(OFFICIAL);

    private @Nullable Grade boy11(FitnessItem item, String value) {
        return table.grade(item, Sex.M, 11, 11 * 12 + 4, new BigDecimal(value));
    }

    @Test
    @DisplayName("유소년 남 11세 1등급 기준값은 공식 기준표와 같다 — 020 ≥ 77 · 028 ≥ 46.5 · 009 ≥ 36 · 012 ≥ 11.5 · 043 ≥ 33 · 022 ≥ 161")
    void 유소년_남_11세_1등급_기준값은_공식_기준표와_같다() {
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "77")).isEqualTo(Grade.FIRST);
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "76")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.RELATIVE_GRIP, "46.5")).isEqualTo(Grade.FIRST);
        assertThat(boy11(FitnessItem.RELATIVE_GRIP, "46.4")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.SIT_UP, "36")).isEqualTo(Grade.FIRST);
        assertThat(boy11(FitnessItem.SIT_UP, "35")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.SIT_AND_REACH, "11.5")).isEqualTo(Grade.FIRST);
        assertThat(boy11(FitnessItem.SIT_AND_REACH, "11.4")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.SIDE_STEP, "33")).isEqualTo(Grade.FIRST);
        assertThat(boy11(FitnessItem.SIDE_STEP, "32")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.STANDING_LONG_JUMP, "161")).isEqualTo(Grade.FIRST);
        assertThat(boy11(FitnessItem.STANDING_LONG_JUMP, "160.9")).isEqualTo(Grade.SECOND);
    }

    @Test
    @DisplayName("1 → 2 → 3등급 차례로 처음 통과한 등급이고, 줄이 있는데 하나도 통과하지 못하면 참가")
    void 처음_통과한_등급이고_하나도_통과하지_못하면_참가() {
        // 020: 1등급 ≥ 77 · 2등급 ≥ 59 · 3등급 ≥ 46
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "150")).isEqualTo(Grade.FIRST);
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "59")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "58")).isEqualTo(Grade.THIRD);
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "46")).isEqualTo(Grade.THIRD);
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "45")).isEqualTo(Grade.PARTICIPATION);
        assertThat(boy11(FitnessItem.SHUTTLE_RUN, "0")).isEqualTo(Grade.PARTICIPATION);
    }

    @Test
    @DisplayName("3등급 줄이 없는 항목(043 · 022)은 2등급에 못 미치면 곧바로 참가")
    void 등급_줄이_없는_항목은_2등급에_못_미치면_참가() {
        assertThat(boy11(FitnessItem.SIDE_STEP, "30")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.SIDE_STEP, "29")).isEqualTo(Grade.PARTICIPATION);
        assertThat(boy11(FitnessItem.STANDING_LONG_JUMP, "147")).isEqualTo(Grade.SECOND);
        assertThat(boy11(FitnessItem.STANDING_LONG_JUMP, "146.9")).isEqualTo(Grade.PARTICIPATION);
    }

    @Test
    @DisplayName("그 사람 · 항목의 기준 줄이 없으면 null — 만 7~10세 · 다른 성별 · 표에 없는 항목 · 어르신")
    void 기준_줄이_없으면_null() {
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN, Sex.M, 9, 9 * 12, new BigDecimal("80")))
                .isNull();
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN, Sex.F, 11, 11 * 12, new BigDecimal("80")))
                .isNull();
        assertThat(table.grade(FitnessItem.EYE_HAND_COORDINATION, Sex.F, 11, 11 * 12, new BigDecimal("5")))
                .isNull();
        assertThat(table.grade(FitnessItem.SIT_AND_REACH, Sex.M, 70, 70 * 12, new BigDecimal("10")))
                .isNull();
        assertThat(GradeTable.EMPTY.grade(FitnessItem.SHUTTLE_RUN, Sex.M, 11, 11 * 12, new BigDecimal("80")))
                .isNull();
    }

    @Test
    @DisplayName("낮을수록 좋은 항목은 기준표의 <= 로 본다 — 청소년 여 15세 013 일리노이 · 017 눈-손협응력")
    void 낮을수록_좋은_항목은_기준표의_작거나_같다로_본다() {
        GradeTable t = table;
        assertThat(t.grade(FitnessItem.ILLINOIS, Sex.F, 15, 180, new BigDecimal("21.4")))
                .isEqualTo(Grade.FIRST);
        assertThat(t.grade(FitnessItem.ILLINOIS, Sex.F, 15, 180, new BigDecimal("21.5")))
                .isEqualTo(Grade.SECOND);
        assertThat(t.grade(FitnessItem.ILLINOIS, Sex.F, 15, 180, new BigDecimal("22.7")))
                .isEqualTo(Grade.SECOND);
        assertThat(t.grade(FitnessItem.ILLINOIS, Sex.F, 15, 180, new BigDecimal("22.8")))
                .isEqualTo(Grade.PARTICIPATION);
        assertThat(t.grade(FitnessItem.EYE_HAND_COORDINATION, Sex.F, 15, 180, new BigDecimal("40")))
                .isEqualTo(Grade.FIRST);
        assertThat(t.grade(FitnessItem.EYE_HAND_COORDINATION, Sex.F, 15, 180, new BigDecimal("60")))
                .isEqualTo(Grade.PARTICIPATION);
    }

    @Test
    @DisplayName("유아기는 개월로 기준 구간을 고른다 — 같은 만 4세라도 53개월과 54개월은 다른 줄")
    void 유아기는_개월로_기준_구간을_고른다() {
        // 020: 48~53개월 1등급 ≥ 48 · 54~59개월 1등급 ≥ 59, 2등급 ≥ 44
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN, Sex.M, 4, 53, new BigDecimal("50")))
                .isEqualTo(Grade.FIRST);
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN, Sex.M, 4, 54, new BigDecimal("50")))
                .isEqualTo(Grade.SECOND);
        // 050 5m 4회 왕복달리기(초, 낮을수록 좋음): 48~53개월 1등급 ≤ 11.06 · 2등급 ≤ 11.73
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 50, new BigDecimal("11.06")))
                .isEqualTo(Grade.FIRST);
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 50, new BigDecimal("11.07")))
                .isEqualTo(Grade.SECOND);
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 50, new BigDecimal("11.74")))
                .isEqualTo(Grade.PARTICIPATION);
        // 47개월은 기준 구간 밖이다(만 4세 미만은 측정도 받지 않는다)
        assertThat(table.grade(FitnessItem.SHUTTLE_RUN, Sex.M, 3, 47, new BigDecimal("50")))
                .isNull();
    }

    @Test
    @DisplayName("한 줄의 비교는 AI Threshold.passes() 와 같다 — >= · <= 는 경계 포함, < 는 경계 제외, between 은 양 끝 포함")
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
        List<GradeThreshold> overlapping = List.of(
                row(AgeGroup.YOUTH, Sex.M, NormAgeUnit.YEARS, 11, 12, Grade.FIRST, "020", ">=", 77, null),
                row(AgeGroup.YOUTH, Sex.M, NormAgeUnit.YEARS, 12, 12, Grade.FIRST, "020", ">=", 91, null));
        assertThatThrownBy(() -> GradeTable.of(overlapping)).isInstanceOf(IllegalArgumentException.class);
        assertThat(table.getSize()).isEqualTo(OFFICIAL.size());
    }
}
