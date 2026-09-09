package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.Band
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class FitnessTestTest {
    private val profileId = UUID.randomUUID()
    private val now = Instant.parse("2026-09-09T00:00:00Z")

    private fun register(
        measurements: List<Measurement>,
        ageAtTest: Int = 9,
        scorer: (FitnessItem, BigDecimal) -> Int? = { _, _ -> 50 },
    ) = FitnessTest.register(
        id = UUID.randomUUID(),
        profileId = profileId,
        testedOn = LocalDate.of(2026, 9, 1),
        source = FitnessTestSource.SELF_INPUT,
        ageAtTest = ageAtTest,
        heightCm = null,
        weightKg = null,
        measurements = measurements,
        scorer = scorer,
        createdAt = now,
    )

    private fun m(
        code: String,
        value: Int,
    ) = Measurement(code, BigDecimal(value))

    @Test
    fun `항목이 없으면 NO_ITEMS`() {
        assertThatThrownBy { register(emptyList()) }.isInstanceOf(NoItemsException::class.java)
    }

    @Test
    fun `혈압 005·006 은 ITEM_NOT_ALLOWED`() {
        assertThatThrownBy { register(listOf(m("028", 30), m("005", 80))) }.isInstanceOf(ItemNotAllowedException::class.java)
        assertThatThrownBy { register(listOf(m("006", 120))) }.isInstanceOf(ItemNotAllowedException::class.java)
    }

    @Test
    fun `카탈로그에 없는 코드는 UNKNOWN_ITEM`() {
        assertThatThrownBy { register(listOf(m("003", 20))) }.isInstanceOf(UnknownItemException::class.java)
    }

    @Test
    fun `연령대 항목이 아니면 ITEM_NOT_FOR_AGE_GROUP`() {
        // 043 반복옆뛰기는 유소년만
        assertThatThrownBy { register(listOf(m("043", 20)), ageAtTest = 30) }
            .isInstanceOf(ItemNotForAgeGroupException::class.java)
    }

    @Test
    fun `같은 항목이 두 번 들어오면 거부한다`() {
        assertThatThrownBy { register(listOf(m("028", 30), m("028", 31))) }.isInstanceOf(DuplicateItemException::class.java)
    }

    @Test
    fun `백분위는 scorer 결과로 굳고 등급·구간이 파생된다`() {
        val test =
            register(listOf(m("028", 40), m("012", 5))) { item, _ ->
                when (item) {
                    FitnessItem.RELATIVE_GRIP -> 80
                    else -> null
                }
            }
        val grip = test.items.first { it.item == FitnessItem.RELATIVE_GRIP }
        assertThat(grip.score).isEqualTo(ItemScore(80, Grade.SECOND, Band.STRENGTH, "상위 20%"))
        val reach = test.items.first { it.item == FitnessItem.SIT_AND_REACH }
        assertThat(reach.score).isEqualTo(ItemScore.NONE)
        assertThat(test.measurements).containsEntry("028", BigDecimal(40)).containsEntry("012", BigDecimal(5))
    }

    @Test
    fun `레이더는 5요인 순서로, 요인에 항목이 여럿이면 평균이고 없으면 null`() {
        val test =
            register(listOf(m("028", 40), m("020", 30), m("035", 40), m("009", 20)), ageAtTest = 15) { item, _ ->
                when (item) {
                    FitnessItem.RELATIVE_GRIP -> 80
                    FitnessItem.SHUTTLE_RUN -> 40
                    FitnessItem.TREADMILL_VO2MAX -> 61
                    FitnessItem.SIT_UP -> null
                    else -> null
                }
            }
        val radar = test.radar()
        assertThat(radar.map { it.factor }).isEqualTo(FitnessFactor.RADAR)
        assertThat(radar.map { it.percentile }).containsExactly(80, null, null, 51, null)
    }

    @Test
    fun `weakest·strongest 는 백분위 있는 항목 중 최소·최대이고 coachDirection 은 weakest 로 정한다`() {
        val growth =
            register(listOf(m("028", 40), m("012", 5), m("009", 20))) { item, _ ->
                when (item) {
                    FitnessItem.RELATIVE_GRIP -> 80
                    FitnessItem.SIT_AND_REACH -> 20
                    else -> null
                }
            }
        assertThat(growth.weakest?.itemCode).isEqualTo("012")
        assertThat(growth.weakest?.factor).isEqualTo(FitnessFactor.FLEXIBILITY)
        assertThat(growth.weakest?.percentile).isEqualTo(20)
        assertThat(growth.strongest?.itemCode).isEqualTo("028")
        assertThat(growth.coachDirection).isEqualTo(CoachDirection.GROWTH)

        val strengthen = register(listOf(m("028", 40), m("012", 5))) { item, _ -> if (item == FitnessItem.RELATIVE_GRIP) 90 else 76 }
        assertThat(strengthen.weakest?.percentile).isEqualTo(76)
        assertThat(strengthen.coachDirection).isEqualTo(CoachDirection.STRENGTHEN)

        val exactly75 = register(listOf(m("028", 40))) { _, _ -> 75 }
        assertThat(exactly75.coachDirection).isEqualTo(CoachDirection.GROWTH)

        val unscored = register(listOf(m("028", 40))) { _, _ -> null }
        assertThat(unscored.weakest).isNull()
        assertThat(unscored.strongest).isNull()
        assertThat(unscored.coachDirection).isEqualTo(CoachDirection.GROWTH)
    }
}
