package kr.ac.kookmin.familyfitness.fitness.application

import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestNotFoundException
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource
import kr.ac.kookmin.familyfitness.fitness.domain.ItemNotAllowedException
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement
import kr.ac.kookmin.familyfitness.fitness.domain.NoFitnessTestException
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionScenario
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryRequest
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryResponse
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.support.anyArg
import kr.ac.kookmin.familyfitness.support.capture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class PredictionServiceTest {
    private val actorId = UUID.randomUUID()
    private val familyId = UUID.randomUUID()
    private val profileId = UUID.randomUUID()

    /** 2026-09-09 12:00 KST */
    private val clock = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneOffset.UTC)
    private val zone = ZoneId.of("Asia/Seoul")

    private val tests = InMemoryFitnessTestRepository()
    private val predictions = InMemoryPredictionRepository()
    private val ai = mock(AiGateway::class.java)
    private val familyAccess = mock(FamilyAccess::class.java)
    private val profileQuery = mock(ProfileQuery::class.java)
    private val service = PredictionService(tests, predictions, ai, familyAccess, profileQuery, clock, zone)

    private fun profile(
        birthDate: LocalDate = LocalDate.of(2017, 5, 1),
        consentGiven: Boolean = true,
        measurable: Boolean = true,
    ) {
        val ageGroup = AgeGroup.of(birthDate, LocalDate.of(2026, 9, 9))
        `when`(familyAccess.requireSameFamilyAsProfile(actorId, profileId))
            .thenReturn(summaryOf(profileId, familyId, ageGroup, measurable, consentRequired = true, consentGiven = consentGiven))
        `when`(profileQuery.findDetails(profileId))
            .thenReturn(detailsOf(profileId, familyId, birthDate, Sex.F, heightCm = BigDecimal("135.0"), weightKg = BigDecimal("31.5")))
    }

    private fun savedTest(
        testedOn: LocalDate = LocalDate.of(2026, 9, 1),
        ageAtTest: Int = 9,
        heightCm: BigDecimal? = null,
    ): FitnessTest =
        tests.save(
            FitnessTest.register(
                id = UUID.randomUUID(),
                profileId = profileId,
                testedOn = testedOn,
                source = FitnessTestSource.SELF_INPUT,
                ageAtTest = ageAtTest,
                heightCm = heightCm,
                weightKg = null,
                measurements = listOf(Measurement("028", BigDecimal("40.5")), Measurement("012", BigDecimal("9"))),
                scorer = { _, _ -> 60 },
                createdAt = clock.instant(),
            ),
        )

    private fun aiResponse(vararg bands: TrajectoryResponse.Band) =
        TrajectoryResponse(
            basis = "cross_sectional_group_distribution",
            itemCode = "028",
            itemName = "상대악력",
            unit = "%",
            bands = bands.toList(),
            notice = "집단 분포를 바탕으로 한 참고 범위입니다. 개인의 변화를 나타내지 않습니다.",
            lowSample = false,
        )

    private fun band(
        age: Int,
        p50: Double,
    ) = TrajectoryResponse.Band(age, p50 - 5, p50, p50 + 5, 100)

    @Test
    fun `측정 기록이 없으면 NO_FITNESS_TEST`() {
        profile()
        assertThatThrownBy { service.predict(actorId, profileId, PredictCommand()) }.isInstanceOf(NoFitnessTestException::class.java)
    }

    @Test
    fun `동의가 철회됐으면 CONSENT_REQUIRED`() {
        profile(consentGiven = false, measurable = false)
        savedTest()
        assertThatThrownBy { service.predict(actorId, profileId, PredictCommand()) }.isInstanceOf(ConsentRequiredException::class.java)
    }

    @Test
    fun `혈압 항목으로는 예측하지 않는다`() {
        profile()
        savedTest()
        assertThatThrownBy { service.predict(actorId, profileId, PredictCommand(itemCode = "005")) }
            .isInstanceOf(ItemNotAllowedException::class.java)
    }

    @Test
    fun `AI 를 부르고 지금 나이 이후 구간만 MAINTAIN 포인트로 저장한다`() {
        profile()
        val test = savedTest(heightCm = BigDecimal("136.2"))
        `when`(ai.trajectory(anyArg())).thenReturn(aiResponse(band(8, 35.0), band(9, 38.0), band(12, 45.0), band(19, 60.0)))

        val prediction = service.predict(actorId, profileId, PredictCommand(horizonYears = 10, itemCode = "028"))

        val captor: ArgumentCaptor<TrajectoryRequest> = ArgumentCaptor.forClass(TrajectoryRequest::class.java)
        verify(ai).trajectory(capture(captor))
        val request = captor.value
        assertThat(request.itemCode).isEqualTo("028")
        assertThat(request.horizonYears).isEqualTo(10)
        assertThat(request.profile.profileRef).isEqualTo(ProfileRef.of(profileId))
        assertThat(request.profile.age).isEqualTo(9)
        assertThat(request.profile.ageUnit).isEqualTo("세")
        assertThat(request.profile.sex).isEqualTo("F")
        assertThat(request.profile.heightCm).isEqualTo(136.2)
        assertThat(request.profile.weightKg).isEqualTo(31.5)
        assertThat(request.profile.measurements).containsExactlyInAnyOrderEntriesOf(mapOf("028" to 40.5, "012" to 9.0))
        assertThat(request.profile.inputLevel).isEqualTo("L2")

        assertThat(prediction.fitnessTestId).isEqualTo(test.id)
        assertThat(prediction.modelVersion).isEqualTo(Prediction.MODEL_VERSION)
        assertThat(prediction.basis).isEqualTo("cross_sectional_group_distribution")
        assertThat(prediction.horizonYears).isEqualTo(10)
        assertThat(prediction.points).allMatch { it.scenario == PredictionScenario.MAINTAIN && it.itemCode == "028" }
        assertThat(prediction.points.map { it.yearsFromNow }).containsExactly(0, 3, 10)
        assertThat(prediction.points[1].p50).isEqualByComparingTo(BigDecimal("45.0"))
        assertThat(prediction.points[1].p10).isEqualByComparingTo(BigDecimal("40.0"))
        assertThat(prediction.points[1].p90).isEqualByComparingTo(BigDecimal("50.0"))
        assertThat(predictions.saved).containsExactly(prediction)
    }

    @Test
    fun `유아기는 개월 단위로 보낸다`() {
        profile(birthDate = LocalDate.of(2021, 3, 9))
        savedTest(ageAtTest = 5)
        `when`(ai.trajectory(anyArg())).thenReturn(aiResponse(band(5, 20.0)))

        service.predict(actorId, profileId, PredictCommand())

        val captor: ArgumentCaptor<TrajectoryRequest> = ArgumentCaptor.forClass(TrajectoryRequest::class.java)
        verify(ai).trajectory(capture(captor))
        assertThat(captor.value.profile.age).isEqualTo(66)
        assertThat(captor.value.profile.ageUnit).isEqualTo("개월")
    }

    @Test
    fun `fitnessTestId 를 주면 그 회차를 쓰고 다른 프로필 것이면 NOT_FOUND`() {
        profile()
        val older = savedTest(testedOn = LocalDate.of(2026, 7, 1))
        savedTest(testedOn = LocalDate.of(2026, 9, 1))
        `when`(ai.trajectory(anyArg())).thenReturn(aiResponse(band(10, 40.0)))

        val prediction = service.predict(actorId, profileId, PredictCommand(fitnessTestId = older.id))
        assertThat(prediction.fitnessTestId).isEqualTo(older.id)

        assertThatThrownBy { service.predict(actorId, profileId, PredictCommand(fitnessTestId = UUID.randomUUID())) }
            .isInstanceOf(FitnessTestNotFoundException::class.java)
    }

    @Test
    fun `AI 장애는 잡지 않고 그대로 올린다`() {
        profile()
        savedTest()
        `when`(ai.trajectory(anyArg())).thenThrow(AiUnavailableException("timeout"))
        assertThatThrownBy { service.predict(actorId, profileId, PredictCommand()) }.isInstanceOf(AiUnavailableException::class.java)
        assertThat(predictions.saved).isEmpty()
    }
}
