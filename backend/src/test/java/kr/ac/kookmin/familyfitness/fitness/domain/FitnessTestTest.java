package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FitnessTestTest {
    private final UUID profileId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-09T00:00:00Z");

    private FitnessTest register(List<Measurement> measurements) {
        return register(measurements, 9, (item, value) -> 50);
    }

    private FitnessTest register(
            List<Measurement> measurements, BiFunction<FitnessItem, BigDecimal, @Nullable Integer> scorer) {
        return register(measurements, 9, scorer);
    }

    private FitnessTest register(
            List<Measurement> measurements,
            int ageAtTest,
            BiFunction<FitnessItem, BigDecimal, @Nullable Integer> scorer) {
        return register(measurements, ageAtTest, scorer, (item, value) -> null);
    }

    private FitnessTest register(
            List<Measurement> measurements,
            int ageAtTest,
            BiFunction<FitnessItem, BigDecimal, @Nullable Integer> scorer,
            BiFunction<FitnessItem, BigDecimal, @Nullable Grade> grader) {
        return FitnessTest.register(
                UUID.randomUUID(),
                profileId,
                LocalDate.of(2026, 9, 1),
                FitnessTestSource.SELF_INPUT,
                ageAtTest,
                BodyMeasures.NONE,
                measurements,
                scorer,
                grader,
                now);
    }

    private static Measurement m(String code, int value) {
        return new Measurement(code, BigDecimal.valueOf(value));
    }

    @Test
    @DisplayName("항목이 없으면 NO_ITEMS")
    void 항목이_없으면_NO_ITEMS() {
        assertThatThrownBy(() -> register(List.of())).isInstanceOf(NoItemsException.class);
    }

    @Test
    @DisplayName("혈압 005·006 은 ITEM_NOT_ALLOWED")
    void 혈압_005_006_은_ITEM_NOT_ALLOWED() {
        assertThatThrownBy(() -> register(List.of(m("028", 30), m("005", 80))))
                .isInstanceOf(ItemNotAllowedException.class);
        assertThatThrownBy(() -> register(List.of(m("006", 120)))).isInstanceOf(ItemNotAllowedException.class);
    }

    @Test
    @DisplayName("카탈로그에 없는 코드는 UNKNOWN_ITEM")
    void 카탈로그에_없는_코드는_UNKNOWN_ITEM() {
        assertThatThrownBy(() -> register(List.of(m("003", 20)))).isInstanceOf(UnknownItemException.class);
    }

    @Test
    @DisplayName("연령대 항목이 아니면 ITEM_NOT_FOR_AGE_GROUP")
    void 연령대_항목이_아니면_ITEM_NOT_FOR_AGE_GROUP() {
        // 043 반복옆뛰기는 유소년만
        assertThatThrownBy(() -> register(List.of(m("043", 20)), 30, (item, value) -> 50))
                .isInstanceOf(ItemNotForAgeGroupException.class);
    }

    @Test
    @DisplayName("항목 범위 밖 값은 ITEM_OUT_OF_RANGE, 양 끝 값은 받는다")
    void 항목_범위_밖_값은_ITEM_OUT_OF_RANGE_양_끝_값은_받는다() {
        // 012 앉아윗몸앞으로굽히기 범위 -30~40
        assertThatThrownBy(() -> register(List.of(m("012", 999))))
                .isInstanceOf(ItemOutOfRangeException.class)
                .hasMessageContaining("012");
        assertThatThrownBy(() -> register(List.of(m("012", -31)))).isInstanceOf(ItemOutOfRangeException.class);
        assertThat(register(List.of(m("012", -30))).getItems()).hasSize(1);
        assertThat(register(List.of(m("012", 40))).getItems()).hasSize(1);
        // 소수 값도 같은 조건 — 014 체공시간 범위 0~2(청소년)
        assertThatThrownBy(() ->
                        register(List.of(new Measurement("014", new BigDecimal("2.01"))), 15, (item, value) -> 50))
                .isInstanceOf(ItemOutOfRangeException.class);
        assertThat(register(List.of(new Measurement("014", new BigDecimal("2.00"))), 15, (item, value) -> 50)
                        .getItems())
                .hasSize(1);
    }

    @Test
    @DisplayName("같은 항목이 두 번 들어오면 거부한다")
    void 같은_항목이_두_번_들어오면_거부한다() {
        assertThatThrownBy(() -> register(List.of(m("028", 30), m("028", 31))))
                .isInstanceOf(DuplicateItemException.class);
    }

    @Test
    @DisplayName("백분위는 scorer 결과로 굳고 구간 · 상위 문구가 파생된다")
    void 백분위는_scorer_결과로_굳고_구간_상위_문구가_파생된다() {
        FitnessTest test = register(
                List.of(m("028", 40), m("012", 5)), (item, value) -> item == FitnessItem.RELATIVE_GRIP ? 80 : null);
        FitnessTestItem grip = test.getItems().stream()
                .filter(it -> it.item() == FitnessItem.RELATIVE_GRIP)
                .findFirst()
                .orElseThrow();
        assertThat(grip.score()).isEqualTo(new ItemScore(80, null, Band.STRENGTH, "상위 20%"));
        FitnessTestItem reach = test.getItems().stream()
                .filter(it -> it.item() == FitnessItem.SIT_AND_REACH)
                .findFirst()
                .orElseThrow();
        assertThat(reach.score()).isEqualTo(ItemScore.NONE);
        assertThat(test.getMeasurements())
                .containsEntry("028", BigDecimal.valueOf(40))
                .containsEntry("012", BigDecimal.valueOf(5));
    }

    @Test
    @DisplayName("등급은 grader(공식 기준표) 결과로 굳고 백분위와 따로다 — 규준이 없어도 등급은 붙는다")
    void 등급은_grader_결과로_굳고_백분위와_따로다() {
        FitnessTest test = register(
                List.of(m("028", 47), m("012", 5), m("043", 20)),
                11,
                (item, value) -> item == FitnessItem.RELATIVE_GRIP ? 50 : null,
                (item, value) -> switch (item) {
                    case RELATIVE_GRIP -> Grade.FIRST;
                    case SIT_AND_REACH -> Grade.THIRD;
                    default -> null;
                });
        assertThat(itemOf(test, FitnessItem.RELATIVE_GRIP).score())
                .isEqualTo(new ItemScore(50, Grade.FIRST, Band.STEADY, "상위 50%"));
        assertThat(itemOf(test, FitnessItem.SIT_AND_REACH).score())
                .isEqualTo(new ItemScore(null, Grade.THIRD, null, null));
        assertThat(itemOf(test, FitnessItem.SIDE_STEP).score()).isEqualTo(ItemScore.NONE);
    }

    @Test
    @DisplayName("복원한 회차는 저장된 등급을 그대로 쓴다 — 백분위에서 다시 셈하지 않는다")
    void 복원한_회차는_저장된_등급을_그대로_쓴다() {
        FitnessTest test = FitnessTest.reconstitute(
                UUID.randomUUID(),
                profileId,
                LocalDate.of(2026, 9, 1),
                FitnessTestSource.SELF_INPUT,
                11,
                BodyMeasures.NONE,
                List.of(
                        new FitnessTest.StoredItem("028", BigDecimal.valueOf(30), 90, "참가"),
                        new FitnessTest.StoredItem("012", BigDecimal.valueOf(12), null, "1등급"),
                        new FitnessTest.StoredItem("020", BigDecimal.valueOf(42), 35, null)),
                now);
        assertThat(itemOf(test, FitnessItem.RELATIVE_GRIP).score())
                .isEqualTo(new ItemScore(90, Grade.PARTICIPATION, Band.STRENGTH, "상위 10%"));
        assertThat(itemOf(test, FitnessItem.SIT_AND_REACH).score())
                .isEqualTo(new ItemScore(null, Grade.FIRST, null, null));
        assertThat(itemOf(test, FitnessItem.SHUTTLE_RUN).score().grade()).isNull();
    }

    private static FitnessTestItem itemOf(FitnessTest test, FitnessItem item) {
        return test.getItems().stream()
                .filter(it -> it.item() == item)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("레이더는 민첩성까지 6요인 순서로, 요인에 항목이 여럿이면 평균이고 없으면 null")
    void 레이더는_민첩성까지_6요인_순서로_요인에_항목이_여럿이면_평균이고_없으면_null() {
        FitnessTest test = register(
                List.of(m("028", 40), m("020", 30), m("035", 40), m("009", 20), m("013", 12)),
                15,
                (item, value) -> switch (item) {
                    case RELATIVE_GRIP -> 80;
                    case SHUTTLE_RUN -> 40;
                    case TREADMILL_VO2MAX -> 61;
                    case ILLINOIS -> 70;
                    default -> null;
                });
        List<RadarPoint> radar = test.radar();
        assertThat(FitnessFactor.RADAR)
                .containsExactly(
                        FitnessFactor.STRENGTH,
                        FitnessFactor.MUSCULAR_ENDURANCE,
                        FitnessFactor.FLEXIBILITY,
                        FitnessFactor.CARDIO,
                        FitnessFactor.POWER,
                        FitnessFactor.AGILITY);
        assertThat(radar.stream().map(RadarPoint::factor).toList()).isEqualTo(FitnessFactor.RADAR);
        assertThat(radar.stream().map(RadarPoint::percentile).toList()).containsExactly(80, null, null, 51, null, 70);
    }

    @Test
    @DisplayName("weakest·strongest 는 백분위 있는 항목 중 최소·최대이고 coachDirection 은 weakest 로 정한다")
    void weakest_strongest_는_백분위_있는_항목_중_최소_최대이고_coachDirection_은_weakest_로_정한다() {
        FitnessTest growth = register(List.of(m("028", 40), m("012", 5), m("009", 20)), (item, value) -> switch (item) {
            case RELATIVE_GRIP -> 80;
            case SIT_AND_REACH -> 20;
            default -> null;
        });
        FactorPoint growthWeakest = growth.getWeakest();
        assertThat(growthWeakest.itemCode()).isEqualTo("012");
        assertThat(growthWeakest.factor()).isEqualTo(FitnessFactor.FLEXIBILITY);
        assertThat(growthWeakest.percentile()).isEqualTo(20);
        assertThat(growth.getStrongest().itemCode()).isEqualTo("028");
        assertThat(growth.getCoachDirection()).isEqualTo(CoachDirection.GROWTH);

        FitnessTest strengthen = register(
                List.of(m("028", 40), m("012", 5)), (item, value) -> item == FitnessItem.RELATIVE_GRIP ? 90 : 76);
        assertThat(strengthen.getWeakest().percentile()).isEqualTo(76);
        assertThat(strengthen.getCoachDirection()).isEqualTo(CoachDirection.STRENGTHEN);

        FitnessTest exactly75 = register(List.of(m("028", 40)), (item, value) -> 75);
        assertThat(exactly75.getCoachDirection()).isEqualTo(CoachDirection.GROWTH);

        FitnessTest unscored = register(List.of(m("028", 40)), (item, value) -> null);
        assertThat(unscored.getWeakest()).isNull();
        assertThat(unscored.getStrongest()).isNull();
        assertThat(unscored.getCoachDirection()).isEqualTo(CoachDirection.GROWTH);
    }
}
