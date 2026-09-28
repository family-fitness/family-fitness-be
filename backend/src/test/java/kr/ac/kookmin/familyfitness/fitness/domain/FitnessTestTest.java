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
        return FitnessTest.register(
                UUID.randomUUID(),
                profileId,
                LocalDate.of(2026, 9, 1),
                FitnessTestSource.SELF_INPUT,
                ageAtTest,
                null,
                null,
                measurements,
                scorer,
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
    @DisplayName("같은 항목이 두 번 들어오면 거부한다")
    void 같은_항목이_두_번_들어오면_거부한다() {
        assertThatThrownBy(() -> register(List.of(m("028", 30), m("028", 31))))
                .isInstanceOf(DuplicateItemException.class);
    }

    @Test
    @DisplayName("백분위는 scorer 결과로 굳고 등급·구간이 파생된다")
    void 백분위는_scorer_결과로_굳고_등급_구간이_파생된다() {
        FitnessTest test = register(
                List.of(m("028", 40), m("012", 5)), (item, value) -> item == FitnessItem.RELATIVE_GRIP ? 80 : null);
        FitnessTestItem grip = test.getItems().stream()
                .filter(it -> it.item() == FitnessItem.RELATIVE_GRIP)
                .findFirst()
                .orElseThrow();
        assertThat(grip.score()).isEqualTo(new ItemScore(80, Grade.SECOND, Band.STRENGTH, "상위 20%"));
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
