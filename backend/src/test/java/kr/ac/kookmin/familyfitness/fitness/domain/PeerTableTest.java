package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 백분위 = AI `stats/tables.py` percentile_of 를 옮긴 것. 아래쪽 자리(searchsorted left)와 위쪽 자리(right)의 가운데를 쓰고,
 * 낮을수록 좋은 항목은 100 에서 빼고, 짝수 쪽으로 반올림한 뒤 0~100 으로 자른다. AI 와 같은지는 AiPercentileParityTest 가
 * AI 가 낸 기대값 전부로 따로 본다.
 */
class PeerTableTest {
    private static List<Double> quantiles(IntToDoubleFunction at) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i <= 100; i++) values.add(at.applyAsDouble(i));
        return values;
    }

    private static PeerQuantiles row(AgeGroup group, Sex sex, int age, String code, int n, List<Double> q) {
        return new PeerQuantiles(group, sex, age, code, n, q);
    }

    /** 유소년 여 11세 028 은 0~100 백분위 값이 백분위와 같다(i → i). */
    private final PeerTable table = PeerTable.of(List.of(
            row(AgeGroup.YOUTH, Sex.F, 11, "028", 500, quantiles(i -> i)),
            // 044 벽패스처럼 0 이 몰린 칸: 0~30 백분위가 0회, 그 위는 1회씩 오른다
            row(AgeGroup.YOUTH, Sex.F, 11, "044", 500, quantiles(i -> i <= 30 ? 0 : i - 30)),
            // 유아기는 개월 — 50개월 남 050(낮을수록 좋다). 분위 값은 오름차순 10초 + 백분위마다 0.1초
            row(AgeGroup.TODDLER, Sex.M, 50, "050", 400, quantiles(i -> 10 + i * 0.1)),
            // 표본이 30 에 못 미치는 칸
            row(AgeGroup.SENIOR, Sex.F, 96, "012", 29, quantiles(i -> i))));

    private @Nullable Integer girl11(FitnessItem item, String value) {
        return table.percentile(item, Sex.F, 11, 11 * 12 + 3, new BigDecimal(value));
    }

    @Test
    @DisplayName("분위 점 위의 값은 아래쪽 자리와 위쪽 자리의 가운데이고 짝수 쪽으로 반올림한다")
    void 분위_점_위의_값은_가운데_자리이고_짝수_쪽으로_반올림한다() {
        // 42 는 42번째 칸 하나 — 아래 자리 42, 위 자리 43, 가운데 42.5 → 짝수 쪽 42
        assertThat(girl11(FitnessItem.RELATIVE_GRIP, "42")).isEqualTo(42);
        // 43 → 43.5 → 44
        assertThat(girl11(FitnessItem.RELATIVE_GRIP, "43")).isEqualTo(44);
        // 칸 사이 값은 아래 · 위 자리가 같다 — 42.3 은 43
        assertThat(girl11(FitnessItem.RELATIVE_GRIP, "42.3")).isEqualTo(43);
    }

    @Test
    @DisplayName("범위 밖은 0 과 100 으로 자른다 — AI 처럼 0 · 100 도 나온다")
    void 범위_밖은_0_과_100_으로_자른다() {
        assertThat(girl11(FitnessItem.RELATIVE_GRIP, "-5")).isZero();
        assertThat(girl11(FitnessItem.RELATIVE_GRIP, "0")).isZero(); // 0 과 1 의 가운데 0.5 → 짝수 쪽 0
        assertThat(girl11(FitnessItem.RELATIVE_GRIP, "100")).isEqualTo(100); // 100.5 → 100
        assertThat(girl11(FitnessItem.RELATIVE_GRIP, "150")).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 값이 몰리면 몰린 칸의 가운데다 — 벽패스 0회는 0 이 아니라 16")
    void 같은_값이_몰리면_몰린_칸의_가운데다() {
        // 0 은 0~30 번째 칸 — 아래 자리 0, 위 자리 31, 가운데 15.5 → 짝수 쪽 16
        assertThat(girl11(FitnessItem.WALL_PASS, "0")).isEqualTo(16);
        assertThat(girl11(FitnessItem.WALL_PASS, "1")).isEqualTo(32); // 31.5 → 32
        assertThat(girl11(FitnessItem.WALL_PASS, "0.5")).isEqualTo(31);
    }

    @Test
    @DisplayName("낮을수록 좋은 항목은 100 에서 자리를 뺀다 — 유아기는 개월로 칸을 고른다")
    void 낮을수록_좋은_항목은_100_에서_자리를_뺀다() {
        // 50개월 050: 값 q[i] = 10 + 0.1 i (오름차순). 10.0 초 → 자리 0.5 → 100 - 0.5 = 99.5 → 짝수 쪽 100
        assertThat(table.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 50, new BigDecimal("10.0")))
                .isEqualTo(100);
        // 15.05 초는 50 · 51 번째 칸 사이 → 자리 51 → 49
        assertThat(table.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 50, new BigDecimal("15.05")))
                .isEqualTo(49);
        assertThat(table.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 50, new BigDecimal("30")))
                .isZero();
        // 같은 만 4세라도 51개월 칸은 없다
        assertThat(table.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 51, new BigDecimal("15")))
                .isNull();
    }

    @Test
    @DisplayName("표본이 30 에 못 미치거나 칸이 없으면 null — 만 7~10세 · 다른 성별 · 표에 없는 항목")
    void 표본이_30_에_못_미치거나_칸이_없으면_null() {
        assertThat(table.percentile(FitnessItem.SIT_AND_REACH, Sex.F, 96, 96 * 12, new BigDecimal("10")))
                .isNull();
        assertThat(table.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 9 * 12, new BigDecimal("30")))
                .isNull();
        assertThat(table.percentile(FitnessItem.RELATIVE_GRIP, Sex.M, 11, 11 * 12, new BigDecimal("30")))
                .isNull();
        assertThat(girl11(FitnessItem.SIT_UP, "30")).isNull();
        assertThat(PeerTable.EMPTY.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 11, 132, BigDecimal.TEN))
                .isNull();
    }

    @Test
    @DisplayName("101칸이 아니거나 오름차순이 아니거나 같은 칸이 두 줄이면 표를 만들지 않는다")
    void 표가_어긋나면_만들지_않는다() {
        assertThatThrownBy(() -> row(AgeGroup.YOUTH, Sex.F, 11, "028", 100, List.of(1.0, 2.0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> row(AgeGroup.YOUTH, Sex.F, 11, "028", 100, quantiles(i -> 100 - i)))
                .isInstanceOf(IllegalArgumentException.class);
        PeerQuantiles one = row(AgeGroup.YOUTH, Sex.F, 11, "028", 100, quantiles(i -> i));
        assertThatThrownBy(() -> PeerTable.of(List.of(one, one))).isInstanceOf(IllegalArgumentException.class);
        assertThat(table.getSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("quantiles 글자는 AI 처럼 ; 로 나눠 읽는다")
    void quantiles_글자는_세미콜론으로_나눠_읽는다() {
        StringBuilder text = new StringBuilder("-30");
        for (int i = 1; i <= 100; i++) text.append(';').append(i * 0.5);
        assertThat(PeerQuantiles.parse(text.toString()))
                .hasSize(101)
                .startsWith(-30.0, 0.5)
                .endsWith(50.0);
    }
}
