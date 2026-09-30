package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FitnessItemCatalogTest {
    private List<String> codes(AgeGroup ageGroup) {
        return FitnessItem.forAgeGroup(ageGroup).stream()
                .map(FitnessItem::getCode)
                .toList();
    }

    @Test
    @DisplayName("연령대별 항목은 계약 표와 같다")
    void 연령대별_항목은_계약_표와_같다() {
        assertThat(codes(AgeGroup.TODDLER)).containsExactlyInAnyOrder("020", "028", "009", "012", "050", "022", "051");
        assertThat(codes(AgeGroup.YOUTH)).containsExactlyInAnyOrder("020", "028", "009", "012", "043", "022");
        assertThat(codes(AgeGroup.ADOLESCENT))
                .containsExactlyInAnyOrder("020", "035", "037", "028", "009", "010", "012", "013", "014", "017");
        assertThat(codes(AgeGroup.ADULT))
                .containsExactlyInAnyOrder("020", "035", "037", "028", "019", "012", "021", "040", "022", "041");
        assertThat(codes(AgeGroup.SENIOR)).containsExactlyInAnyOrder("012", "028", "019");
    }

    @Test
    @DisplayName("EASY 항목이 먼저 오고 EQUIPMENT 항목은 선택이다")
    void EASY_항목이_먼저_오고_EQUIPMENT_항목은_선택이다() {
        List<FitnessItem> youth = FitnessItem.forAgeGroup(AgeGroup.YOUTH);
        assertThat(youth.stream().map(FitnessItem::getCode).toList())
                .containsExactly("009", "012", "043", "020", "022", "028");
        assertThat(youth.stream()
                        .filter(it -> it.getInputGroup() == InputGroup.EASY)
                        .toList())
                .allMatch(it -> !it.isOptional() && it.getEquipment() == null);
        assertThat(youth.stream()
                        .filter(it -> it.getInputGroup() == InputGroup.EQUIPMENT)
                        .toList())
                .allMatch(it -> it.isOptional() && it.getEquipment() != null);
    }

    @Test
    @DisplayName("입력 그룹은 계약과 같다")
    void 입력_그룹은_계약과_같다() {
        List<String> easy = Arrays.stream(FitnessItem.values())
                .filter(it -> it.getInputGroup() == InputGroup.EASY)
                .map(FitnessItem::getCode)
                .toList();
        assertThat(easy).containsExactlyInAnyOrder("009", "010", "012", "014", "019", "041", "043");
        List<String> equipment = Arrays.stream(FitnessItem.values())
                .filter(it -> it.getInputGroup() == InputGroup.EQUIPMENT)
                .map(FitnessItem::getCode)
                .toList();
        assertThat(equipment)
                .containsExactlyInAnyOrder("028", "020", "022", "050", "021", "013", "035", "037", "040", "017", "051");
        assertThat(FitnessItem.RELATIVE_GRIP.getEquipment()).isEqualTo("악력계");
    }

    @Test
    @DisplayName("020 라벨은 연령대별 왕복 거리를 붙인다")
    void 라벨은_연령대별_왕복_거리를_붙인다() {
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.TODDLER)).isEqualTo("10m 왕복오래달리기");
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.YOUTH)).isEqualTo("15m 왕복오래달리기");
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.ADOLESCENT)).isEqualTo("20m 왕복오래달리기");
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.ADULT)).isEqualTo("20m 왕복오래달리기");
        assertThat(FitnessItem.RELATIVE_GRIP.label(AgeGroup.YOUTH)).isEqualTo("상대악력");
    }

    @Test
    @DisplayName("방향·단위·요인·범위는 계약 표와 같다")
    void 방향_단위_요인_범위는_계약_표와_같다() {
        assertThat(Arrays.stream(FitnessItem.values())
                        .filter(it -> !it.isHigherIsBetter())
                        .map(FitnessItem::getCode)
                        .toList())
                .containsExactlyInAnyOrder("013", "017", "021", "040", "050", "051");
        assertThat(FitnessItem.RELATIVE_GRIP.getUnit()).isEqualTo("%");
        assertThat(FitnessItem.RELATIVE_GRIP.getFactor()).isEqualTo(FitnessFactor.STRENGTH);
        assertThat(FitnessItem.SIT_AND_REACH.getRange()).isEqualTo(new ValueRange(-30, 40));
        assertThat(FitnessItem.STANDING_LONG_JUMP.getRange()).isEqualTo(new ValueRange(0, 350));
    }

    @Test
    @DisplayName("혈압은 ITEM_NOT_ALLOWED, 신체조성과 모르는 코드는 UNKNOWN_ITEM")
    void 혈압은_ITEM_NOT_ALLOWED_신체조성과_모르는_코드는_UNKNOWN_ITEM() {
        assertThatThrownBy(() -> FitnessItem.resolve("005")).isInstanceOf(ItemNotAllowedException.class);
        assertThatThrownBy(() -> FitnessItem.resolve("006")).isInstanceOf(ItemNotAllowedException.class);
        assertThatThrownBy(() -> FitnessItem.resolve("003")).isInstanceOf(UnknownItemException.class);
        assertThatThrownBy(() -> FitnessItem.resolve("999")).isInstanceOf(UnknownItemException.class);
        assertThat(FitnessItem.resolve("028")).isEqualTo(FitnessItem.RELATIVE_GRIP);
    }
}
