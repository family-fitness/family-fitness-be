package kr.ac.kookmin.familyfitness.fitness.domain;

import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.peers;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessAgeTable.Bound;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessAgeTable.Estimate;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessAgeTable.ItemAge;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 곧은 분포로 만든 성인 표(만 19~64세)로 체력 나이 셈을 확인한다.
 * <ul>
 *   <li>020 왕복오래달리기(높을수록 좋다): 중앙값이 19세 60회에서 한 살마다 1회씩 준다.
 *   <li>021 10m 4회 왕복달리기(낮을수록 좋다): 중앙값이 19세 10초에서 한 살마다 0.1초씩 는다.
 *   <li>012 앉아윗몸앞으로굽히기: 중앙값이 10, 11cm 를 오간다 — 나이와 관계가 없어 빠져야 한다.
 * </ul>
 */
class FitnessAgeTableTest {
    private FitnessAgeTable table;

    private static List<PeerQuantiles> adultRows() {
        List<PeerQuantiles> rows = new ArrayList<>();
        for (int age = 19; age <= 64; age++) {
            double run = 60 - (age - 19);
            rows.add(peers(AgeGroup.ADULT, Sex.M, age, "020", run - 10, 0.2));
            double sprint = 10 + (age - 19) * 0.1;
            rows.add(peers(AgeGroup.ADULT, Sex.M, age, "021", sprint - 1, 0.02));
            double reach = age % 2 == 0 ? 10 : 11;
            rows.add(peers(AgeGroup.ADULT, Sex.M, age, "012", reach - 5, 0.1));
        }
        return rows;
    }

    @BeforeEach
    void setUp() {
        table = FitnessAgeTable.of(adultRows());
    }

    @Test
    @DisplayName("나이가 들수록 한 방향으로 나빠지는 항목만 쓴다")
    void 나이와_관계가_약한_항목은_뺀다() {
        assertThat(table.usable(Sex.M, FitnessItem.SHUTTLE_RUN)).isTrue();
        assertThat(table.usable(Sex.M, FitnessItem.SHUTTLE_RUN_10M_X4)).isTrue();
        assertThat(table.usable(Sex.M, FitnessItem.SIT_AND_REACH)).isFalse();
        assertThat(table.trend(Sex.M, FitnessItem.SIT_AND_REACH)).isNotNull();
        assertThat(table.usable(Sex.F, FitnessItem.SHUTTLE_RUN)).isFalse();
    }

    @Test
    @DisplayName("기록이 그 나이의 중앙값과 같으면 그 나이가 항목 나이다")
    void 중앙값에_오는_나이() {
        ItemAge run = table.itemAge(Sex.M, FitnessItem.SHUTTLE_RUN, new BigDecimal("40"));
        assertThat(run).isNotNull();
        assertThat(run.age()).isEqualTo(39.0);
        assertThat(run.bound()).isEqualTo(Bound.NONE);

        // 낮을수록 좋은 항목도 같은 셈이다: 13.0초는 49세의 중앙값
        ItemAge sprint = table.itemAge(Sex.M, FitnessItem.SHUTTLE_RUN_10M_X4, new BigDecimal("13.0"));
        assertThat(sprint).isNotNull();
        assertThat(sprint.age()).isEqualTo(49.0);
    }

    @Test
    @DisplayName("19세 중앙값보다 좋으면 19세(YOUNGER), 64세 중앙값보다 나쁘면 64세(OLDER)로 둔다")
    void 표의_끝에_걸리면_표시한다() {
        ItemAge younger = table.itemAge(Sex.M, FitnessItem.SHUTTLE_RUN, new BigDecimal("70"));
        assertThat(younger).isNotNull();
        assertThat(younger.age()).isEqualTo(19.0);
        assertThat(younger.bound()).isEqualTo(Bound.YOUNGER);

        ItemAge older = table.itemAge(Sex.M, FitnessItem.SHUTTLE_RUN, new BigDecimal("5"));
        assertThat(older).isNotNull();
        assertThat(older.age()).isEqualTo(64.0);
        assertThat(older.bound()).isEqualTo(Bound.OLDER);
    }

    @Test
    @DisplayName("체력 나이는 쓸 수 있는 항목 나이의 평균이고, 뺀 항목은 따로 알려 준다")
    void 항목_나이_평균() {
        Map<FitnessItem, BigDecimal> measured = new LinkedHashMap<>();
        measured.put(FitnessItem.SHUTTLE_RUN, new BigDecimal("40"));
        measured.put(FitnessItem.SHUTTLE_RUN_10M_X4, new BigDecimal("13.0"));
        measured.put(FitnessItem.SIT_AND_REACH, new BigDecimal("10"));

        Estimate estimate = table.estimate(Sex.M, measured);

        assertThat(estimate.fitnessAge()).isEqualTo(44);
        assertThat(estimate.items()).extracting(a -> a.item().getCode()).containsExactly("020", "021");
        assertThat(estimate.excluded()).containsExactly(FitnessItem.SIT_AND_REACH);
    }

    @Test
    @DisplayName("쓸 수 있는 항목이 하나뿐이면 체력 나이를 내지 않는다")
    void 항목이_모자라면_내지_않는다() {
        Estimate estimate = table.estimate(Sex.M, Map.of(FitnessItem.SHUTTLE_RUN, new BigDecimal("40")));

        assertThat(estimate.fitnessAge()).isNull();
        assertThat(estimate.items()).hasSize(1);
    }

    @Test
    @DisplayName("성인 밖의 칸과 표본이 30명 미만인 칸은 쓰지 않는다")
    void 성인_밖과_작은_표본은_건너뛴다() {
        List<PeerQuantiles> rows = new ArrayList<>(adultRows());
        rows.add(peers(AgeGroup.SENIOR, Sex.M, 70, "020", 0, 0.1));
        List<Double> small = new ArrayList<>();
        for (int i = 0; i <= 100; i++) small.add(1.0 * i);
        rows.add(new PeerQuantiles(AgeGroup.ADULT, Sex.F, 30, "020", 29, small));

        FitnessAgeTable withExtra = FitnessAgeTable.of(rows);

        assertThat(withExtra.usable(Sex.F, FitnessItem.SHUTTLE_RUN)).isFalse();
        ItemAge run = withExtra.itemAge(Sex.M, FitnessItem.SHUTTLE_RUN, new BigDecimal("15"));
        assertThat(run).isNotNull();
        assertThat(run.age()).isEqualTo(64.0);
    }

    @Test
    void 순위상관() {
        assertThat(FitnessAgeTable.spearman(new int[] {1, 2, 3, 4}, new double[] {10, 8, 6, 4}))
                .isEqualTo(-1.0);
        assertThat(FitnessAgeTable.spearman(new int[] {1, 2, 3}, new double[] {5, 5, 5}))
                .isEqualTo(0.0);
    }
}
