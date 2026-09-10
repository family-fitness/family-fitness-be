package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Band
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GradeTest {
    @Test
    fun `등급은 잠정 임계값 90·75·50 으로 나뉜다`() {
        assertThat(Grade.ofPercentile(99)).isEqualTo(Grade.FIRST)
        assertThat(Grade.ofPercentile(90)).isEqualTo(Grade.FIRST)
        assertThat(Grade.ofPercentile(89)).isEqualTo(Grade.SECOND)
        assertThat(Grade.ofPercentile(75)).isEqualTo(Grade.SECOND)
        assertThat(Grade.ofPercentile(74)).isEqualTo(Grade.THIRD)
        assertThat(Grade.ofPercentile(50)).isEqualTo(Grade.THIRD)
        assertThat(Grade.ofPercentile(49)).isEqualTo(Grade.PARTICIPATION)
        assertThat(Grade.ofPercentile(1)).isEqualTo(Grade.PARTICIPATION)
    }

    @Test
    fun `백분위 하나에서 등급·구간·상위 문구가 함께 파생된다`() {
        val score = ItemScore.ofPercentile(24)
        assertThat(score.grade).isEqualTo(Grade.PARTICIPATION)
        assertThat(score.band).isEqualTo(Band.GROWTH)
        assertThat(score.topPercentText).isEqualTo("상위 76%")

        val strong = ItemScore.ofPercentile(75)
        assertThat(strong.grade).isEqualTo(Grade.SECOND)
        assertThat(strong.band).isEqualTo(Band.STRENGTH)
        assertThat(strong.topPercentText).isEqualTo("상위 25%")
    }

    @Test
    fun `규준이 없으면 전부 null`() {
        assertThat(ItemScore.ofPercentile(null)).isEqualTo(ItemScore(null, null, null, null))
    }
}

class FitnessItemCatalogTest {
    private fun codes(ageGroup: AgeGroup) = FitnessItem.forAgeGroup(ageGroup).map { it.code }

    @Test
    fun `연령대별 항목은 계약 표와 같다`() {
        assertThat(codes(AgeGroup.TODDLER)).containsExactlyInAnyOrder("020", "028", "009", "012", "050", "022", "051")
        assertThat(codes(AgeGroup.YOUTH)).containsExactlyInAnyOrder("020", "028", "009", "012", "043", "022")
        assertThat(codes(AgeGroup.ADOLESCENT))
            .containsExactlyInAnyOrder("020", "035", "037", "028", "009", "010", "012", "013", "014", "017")
        assertThat(codes(AgeGroup.ADULT))
            .containsExactlyInAnyOrder("020", "035", "037", "028", "019", "012", "021", "040", "022", "041")
        assertThat(codes(AgeGroup.SENIOR)).containsExactlyInAnyOrder("012", "028", "019")
    }

    @Test
    fun `EASY 항목이 먼저 오고 EQUIPMENT 항목은 선택이다`() {
        val youth = FitnessItem.forAgeGroup(AgeGroup.YOUTH)
        assertThat(youth.map { it.code }).containsExactly("009", "012", "043", "020", "022", "028")
        assertThat(youth.filter { it.inputGroup == InputGroup.EASY }).allMatch { !it.optional && it.equipment == null }
        assertThat(youth.filter { it.inputGroup == InputGroup.EQUIPMENT }).allMatch { it.optional && it.equipment != null }
    }

    @Test
    fun `입력 그룹은 계약과 같다`() {
        val easy = FitnessItem.entries.filter { it.inputGroup == InputGroup.EASY }.map { it.code }
        assertThat(easy).containsExactlyInAnyOrder("009", "010", "012", "014", "019", "041", "043")
        val equipment = FitnessItem.entries.filter { it.inputGroup == InputGroup.EQUIPMENT }.map { it.code }
        assertThat(equipment).containsExactlyInAnyOrder("028", "020", "022", "050", "021", "013", "035", "037", "040", "017", "051")
        assertThat(FitnessItem.RELATIVE_GRIP.equipment).isEqualTo("악력계")
    }

    @Test
    fun `020 라벨은 연령대별 왕복 거리를 붙인다`() {
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.TODDLER)).isEqualTo("10m 왕복오래달리기")
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.YOUTH)).isEqualTo("15m 왕복오래달리기")
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.ADOLESCENT)).isEqualTo("20m 왕복오래달리기")
        assertThat(FitnessItem.SHUTTLE_RUN.label(AgeGroup.ADULT)).isEqualTo("20m 왕복오래달리기")
        assertThat(FitnessItem.RELATIVE_GRIP.label(AgeGroup.YOUTH)).isEqualTo("상대악력")
    }

    @Test
    fun `방향·단위·요인·범위는 계약 표와 같다`() {
        assertThat(FitnessItem.entries.filter { !it.higherIsBetter }.map { it.code })
            .containsExactlyInAnyOrder("013", "017", "021", "040", "050", "051")
        assertThat(FitnessItem.RELATIVE_GRIP.unit).isEqualTo("%")
        assertThat(FitnessItem.RELATIVE_GRIP.factor).isEqualTo(FitnessFactor.STRENGTH)
        assertThat(FitnessItem.SIT_AND_REACH.range).isEqualTo(ValueRange(-30, 40))
        assertThat(FitnessItem.STANDING_LONG_JUMP.range).isEqualTo(ValueRange(0, 350))
    }

    @Test
    fun `혈압은 ITEM_NOT_ALLOWED, 신체조성과 모르는 코드는 UNKNOWN_ITEM`() {
        assertThatThrownBy { FitnessItem.resolve("005") }.isInstanceOf(ItemNotAllowedException::class.java)
        assertThatThrownBy { FitnessItem.resolve("006") }.isInstanceOf(ItemNotAllowedException::class.java)
        assertThatThrownBy { FitnessItem.resolve("003") }.isInstanceOf(UnknownItemException::class.java)
        assertThatThrownBy { FitnessItem.resolve("999") }.isInstanceOf(UnknownItemException::class.java)
        assertThat(FitnessItem.resolve("028")).isEqualTo(FitnessItem.RELATIVE_GRIP)
    }
}
