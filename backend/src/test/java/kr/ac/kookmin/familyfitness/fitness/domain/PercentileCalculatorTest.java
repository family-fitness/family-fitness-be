package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 계약 §0 「백분위·등급 계산」: 선형 보간 · 표 밖 끝점 · ↓ 반전 · 1~99 · 규준 없으면 null. */
class PercentileCalculatorTest {
    private record Pair(int percentile, double value) {}

    private static List<NormPoint> points(FitnessItem item, Sex sex, int ageFrom, int ageTo, Pair... pairs) {
        return points(item, sex, ageFrom, ageTo, 1900, pairs);
    }

    private static List<NormPoint> points(FitnessItem item, Sex sex, int ageFrom, int ageTo, int year, Pair... pairs) {
        List<NormPoint> result = new ArrayList<>();
        for (Pair pair : pairs) {
            result.add(new NormPoint(item.getCode(), sex, ageFrom, ageTo, pair.percentile(), pair.value(), year));
        }
        return result;
    }

    private final List<NormPoint> grip = points(
            FitnessItem.RELATIVE_GRIP,
            Sex.F,
            7,
            12,
            new Pair(5, 22.0),
            new Pair(10, 25.0),
            new Pair(25, 30.0),
            new Pair(50, 36.0),
            new Pair(75, 42.0),
            new Pair(90, 48.0),
            new Pair(95, 52.0));
    private final List<NormPoint> shuttle5m = points(
            FitnessItem.SHUTTLE_RUN_5M_X4,
            Sex.M,
            4,
            6,
            new Pair(5, 13.0),
            new Pair(25, 11.2),
            new Pair(50, 10.2),
            new Pair(75, 9.3),
            new Pair(95, 8.2));
    private final PercentileCalculator calculator = new PercentileCalculator(NormTable.of(concat(grip, shuttle5m)));

    private static List<NormPoint> concat(List<NormPoint> a, List<NormPoint> b) {
        List<NormPoint> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    @Test
    @DisplayName("규준 포인트 위의 값은 그 백분위다")
    void 규준_포인트_위의_값은_그_백분위다() {
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 36.0))
                .isEqualTo(50);
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 12, 42.0))
                .isEqualTo(75);
    }

    @Test
    @DisplayName("포인트 사이는 선형 보간하고 반올림한다")
    void 포인트_사이는_선형_보간하고_반올림한다() {
        // 36 → 50, 42 → 75. 39 는 정확히 중간 → 62.5 → 63
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 39.0))
                .isEqualTo(63);
        // 30 → 25, 36 → 50. 32 → 25 + 2/6*25 = 33.3 → 33
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 32.0))
                .isEqualTo(33);
    }

    @Test
    @DisplayName("표 밖은 끝점 백분위로 자른다")
    void 표_밖은_끝점_백분위로_자른다() {
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 1.0))
                .isEqualTo(5);
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 999.0))
                .isEqualTo(95);
    }

    @Test
    @DisplayName("낮을수록 좋은 항목은 값이 작을수록 백분위가 높다")
    void 낮을수록_좋은_항목은_값이_작을수록_백분위가_높다() {
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 8.2))
                .isEqualTo(95);
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 13.0))
                .isEqualTo(5);
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 7.0))
                .isEqualTo(95);
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 20.0))
                .isEqualTo(5);
        // 11.2 → 25, 10.2 → 50 : 10.7 은 중간 → 37.5 → 38
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 10.7))
                .isEqualTo(38);
    }

    @Test
    @DisplayName("결과는 1~99 로 잘라 0과 100 이 나오지 않는다")
    void 결과는_1_99_로_잘라_0과_100_이_나오지_않는다() {
        List<NormPoint> extreme =
                points(FitnessItem.SIT_UP, Sex.M, 7, 12, new Pair(0, 0.0), new Pair(50, 40.0), new Pair(100, 100.0));
        PercentileCalculator calc = new PercentileCalculator(NormTable.of(extreme));
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 0.0)).isEqualTo(1);
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 500.0)).isEqualTo(99);
    }

    @Test
    @DisplayName("나이 구간이나 성별 규준이 없으면 null")
    void 나이_구간이나_성별_규준이_없으면_null() {
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 13, 36.0))
                .isNull();
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.M, 9, 36.0))
                .isNull();
        assertThat(calculator.percentile(FitnessItem.SIT_UP, Sex.F, 9, 36.0)).isNull();
    }

    @Test
    @DisplayName("같은 구간에 연도가 여럿이면 최신 source_year 만 쓴다")
    void 같은_구간에_연도가_여럿이면_최신_source_year_만_쓴다() {
        List<NormPoint> old = points(FitnessItem.SIT_UP, Sex.M, 7, 12, 1900, new Pair(5, 10.0), new Pair(95, 100.0));
        List<NormPoint> recent = points(FitnessItem.SIT_UP, Sex.M, 7, 12, 2024, new Pair(5, 50.0), new Pair(95, 60.0));
        PercentileCalculator calc = new PercentileCalculator(NormTable.of(concat(old, recent)));
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 55.0)).isEqualTo(50);
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 10.0)).isEqualTo(5);
    }

    @Test
    @DisplayName("빈 규준표는 항상 null")
    void 빈_규준표는_항상_null() {
        assertThat(new PercentileCalculator(NormTable.EMPTY).percentile(FitnessItem.SIT_UP, Sex.M, 8, 10.0))
                .isNull();
    }
}
