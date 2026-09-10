package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.Sex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 계약 §0 「백분위·등급 계산」: 선형 보간 · 표 밖 끝점 · ↓ 반전 · 1~99 · 규준 없으면 null. */
class PercentileCalculatorTest {
    private fun points(
        item: FitnessItem,
        sex: Sex,
        ageFrom: Int,
        ageTo: Int,
        vararg pairs: Pair<Int, Double>,
        year: Int = 1900,
    ) = pairs.map { (p, v) -> NormPoint(item.code, sex, ageFrom, ageTo, p, v, year) }

    private val grip =
        points(FitnessItem.RELATIVE_GRIP, Sex.F, 7, 12, 5 to 22.0, 10 to 25.0, 25 to 30.0, 50 to 36.0, 75 to 42.0, 90 to 48.0, 95 to 52.0)
    private val shuttle5m =
        points(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 4, 6, 5 to 13.0, 25 to 11.2, 50 to 10.2, 75 to 9.3, 95 to 8.2)
    private val calculator = PercentileCalculator(NormTable.of(grip + shuttle5m))

    @Test
    fun `규준 포인트 위의 값은 그 백분위다`() {
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 36.0)).isEqualTo(50)
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 12, 42.0)).isEqualTo(75)
    }

    @Test
    fun `포인트 사이는 선형 보간하고 반올림한다`() {
        // 36 → 50, 42 → 75. 39 는 정확히 중간 → 62.5 → 63
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 39.0)).isEqualTo(63)
        // 30 → 25, 36 → 50. 32 → 25 + 2/6*25 = 33.3 → 33
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 32.0)).isEqualTo(33)
    }

    @Test
    fun `표 밖은 끝점 백분위로 자른다`() {
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 1.0)).isEqualTo(5)
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 9, 999.0)).isEqualTo(95)
    }

    @Test
    fun `낮을수록 좋은 항목은 값이 작을수록 백분위가 높다`() {
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 8.2)).isEqualTo(95)
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 13.0)).isEqualTo(5)
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 7.0)).isEqualTo(95)
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 20.0)).isEqualTo(5)
        // 11.2 → 25, 10.2 → 50 : 10.7 은 중간 → 37.5 → 38
        assertThat(calculator.percentile(FitnessItem.SHUTTLE_RUN_5M_X4, Sex.M, 5, 10.7)).isEqualTo(38)
    }

    @Test
    fun `결과는 1~99 로 잘라 0과 100 이 나오지 않는다`() {
        val extreme = points(FitnessItem.SIT_UP, Sex.M, 7, 12, 0 to 0.0, 50 to 40.0, 100 to 100.0)
        val calc = PercentileCalculator(NormTable.of(extreme))
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 0.0)).isEqualTo(1)
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 500.0)).isEqualTo(99)
    }

    @Test
    fun `나이 구간이나 성별 규준이 없으면 null`() {
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.F, 13, 36.0)).isNull()
        assertThat(calculator.percentile(FitnessItem.RELATIVE_GRIP, Sex.M, 9, 36.0)).isNull()
        assertThat(calculator.percentile(FitnessItem.SIT_UP, Sex.F, 9, 36.0)).isNull()
    }

    @Test
    fun `같은 구간에 연도가 여럿이면 최신 source_year 만 쓴다`() {
        val old = points(FitnessItem.SIT_UP, Sex.M, 7, 12, 5 to 10.0, 95 to 100.0, year = 1900)
        val new = points(FitnessItem.SIT_UP, Sex.M, 7, 12, 5 to 50.0, 95 to 60.0, year = 2024)
        val calc = PercentileCalculator(NormTable.of(old + new))
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 55.0)).isEqualTo(50)
        assertThat(calc.percentile(FitnessItem.SIT_UP, Sex.M, 8, 10.0)).isEqualTo(5)
    }

    @Test
    fun `빈 규준표는 항상 null`() {
        assertThat(PercentileCalculator(NormTable.EMPTY).percentile(FitnessItem.SIT_UP, Sex.M, 8, 10.0)).isNull()
    }
}
