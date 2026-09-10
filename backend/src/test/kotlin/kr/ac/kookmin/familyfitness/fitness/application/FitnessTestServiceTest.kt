package kr.ac.kookmin.familyfitness.fitness.application

import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException
import kr.ac.kookmin.familyfitness.fitness.domain.DuplicateDateException
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource
import kr.ac.kookmin.familyfitness.fitness.domain.FutureTestDateException
import kr.ac.kookmin.familyfitness.fitness.domain.Grade
import kr.ac.kookmin.familyfitness.fitness.domain.ItemNotForAgeGroupException
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement
import kr.ac.kookmin.familyfitness.fitness.domain.NoItemsException
import kr.ac.kookmin.familyfitness.fitness.domain.NotMeasurableException
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Band
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class FitnessTestServiceTest {
    private val actorId = UUID.randomUUID()
    private val familyId = UUID.randomUUID()
    private val profileId = UUID.randomUUID()

    /** 2026-09-09 12:00 KST */
    private val clock = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneOffset.UTC)
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 9, 9)

    private val tests = InMemoryFitnessTestRepository()
    private val familyAccess = mock(FamilyAccess::class.java)
    private val profileQuery = mock(ProfileQuery::class.java)
    private val norms =
        NormCatalog(
            StaticNormRepository(
                norms("028", Sex.F, 7, 12, 5 to 22.0, 25 to 30.0, 50 to 36.0, 75 to 42.0, 95 to 52.0) +
                    norms("012", Sex.F, 7, 12, 5 to -2.0, 50 to 9.0, 95 to 20.0),
            ),
        ).apply { refresh() }
    private val service = FitnessTestService(tests, norms, familyAccess, profileQuery, clock, zone)

    private fun childProfile(
        birthDate: LocalDate = LocalDate.of(2017, 5, 1),
        consentRequired: Boolean = true,
        consentGiven: Boolean = true,
        measurable: Boolean = true,
    ) {
        `when`(familyAccess.requireSameFamilyAsProfile(actorId, profileId))
            .thenReturn(summaryOf(profileId, familyId, AgeGroup.YOUTH, measurable, consentRequired, consentGiven))
        `when`(profileQuery.findDetails(profileId)).thenReturn(detailsOf(profileId, familyId, birthDate, Sex.F))
    }

    private fun command(
        testedOn: LocalDate = LocalDate.of(2026, 9, 1),
        vararg items: Pair<String, Int>,
    ) = RegisterFitnessTestCommand(
        testedOn = testedOn,
        source = FitnessTestSource.SELF_INPUT,
        heightCm = null,
        weightKg = null,
        measurements = items.map { (code, v) -> Measurement(code, BigDecimal(v)) },
    )

    @Test
    fun `같은 가족이 아니면 identity 의 예외가 그대로 올라간다`() {
        `when`(familyAccess.requireSameFamilyAsProfile(actorId, profileId)).thenThrow(NotSameFamilyException())
        assertThatThrownBy { service.register(actorId, profileId, command(items = arrayOf("028" to 36))) }
            .isInstanceOf(NotSameFamilyException::class.java)
        assertThat(tests.saved).isEmpty()
    }

    @Test
    fun `등록하면 백분위가 규준으로 계산돼 굳고 등급·구간이 붙는다`() {
        childProfile()
        val test = service.register(actorId, profileId, command(items = arrayOf("028" to 42, "012" to 9)))

        assertThat(test.ageAtTest).isEqualTo(9)
        assertThat(test.ageGroup).isEqualTo(AgeGroup.YOUTH)
        val grip = test.items.first { it.item.code == "028" }
        assertThat(grip.percentile).isEqualTo(75)
        assertThat(grip.score.grade).isEqualTo(Grade.SECOND)
        assertThat(grip.score.band).isEqualTo(Band.STRENGTH)
        assertThat(test.items.first { it.item.code == "012" }.percentile).isEqualTo(50)
        assertThat(test.weakest?.itemCode).isEqualTo("012")
        assertThat(test.strongest?.itemCode).isEqualTo("028")
        assertThat(tests.saved).containsKey(test.id)

        // 규준표가 바뀌어도 저장된 값은 그대로다
        assertThat(
            tests
                .findById(test.id)!!
                .items
                .first { it.item.code == "028" }
                .percentile,
        ).isEqualTo(75)
    }

    @Test
    fun `규준 없는 항목은 백분위 null 로 저장된다`() {
        childProfile()
        val test = service.register(actorId, profileId, command(items = arrayOf("009" to 20)))
        assertThat(test.items.single().percentile).isNull()
        assertThat(test.weakest).isNull()
    }

    @Test
    fun `동의가 필요한데 없거나 철회됐으면 CONSENT_REQUIRED`() {
        childProfile(consentRequired = true, consentGiven = false, measurable = false)
        assertThatThrownBy { service.register(actorId, profileId, command(items = arrayOf("028" to 36))) }
            .isInstanceOf(ConsentRequiredException::class.java)
        assertThat(tests.saved).isEmpty()
    }

    @Test
    fun `동의 불필요(만 14세 이상)면 동의 없이도 저장한다`() {
        childProfile(birthDate = LocalDate.of(2010, 1, 1), consentRequired = false, consentGiven = false)
        val test = service.register(actorId, profileId, command(items = arrayOf("028" to 36)))
        assertThat(test.ageGroup).isEqualTo(AgeGroup.ADOLESCENT)
    }

    @Test
    fun `측정일 기준 만 4세 미만이면 NOT_MEASURABLE`() {
        childProfile(birthDate = LocalDate.of(2023, 1, 1), measurable = false)
        assertThatThrownBy { service.register(actorId, profileId, command(items = arrayOf("028" to 36))) }
            .isInstanceOf(NotMeasurableException::class.java)
    }

    @Test
    fun `측정일이 미래면 400`() {
        childProfile()
        assertThatThrownBy { service.register(actorId, profileId, command(testedOn = today.plusDays(1), items = arrayOf("028" to 36))) }
            .isInstanceOf(FutureTestDateException::class.java)
        service.register(actorId, profileId, command(testedOn = today, items = arrayOf("028" to 36)))
    }

    @Test
    fun `같은 날짜 측정이 이미 있으면 DUPLICATE_DATE`() {
        childProfile()
        service.register(actorId, profileId, command(items = arrayOf("028" to 36)))
        assertThatThrownBy { service.register(actorId, profileId, command(items = arrayOf("028" to 37))) }
            .isInstanceOf(DuplicateDateException::class.java)
        assertThat(tests.saved).hasSize(1)
    }

    @Test
    fun `항목 규칙 위반은 저장하지 않는다`() {
        childProfile()
        assertThatThrownBy { service.register(actorId, profileId, command()) }.isInstanceOf(NoItemsException::class.java)
        assertThatThrownBy { service.register(actorId, profileId, command(items = arrayOf("013" to 20))) }
            .isInstanceOf(ItemNotForAgeGroupException::class.java)
        assertThat(tests.saved).isEmpty()
    }

    @Test
    fun `최신 회차는 testedOn 이 가장 늦은 것이고 없으면 null`() {
        childProfile()
        assertThat(service.latest(actorId, profileId)).isNull()
        service.register(actorId, profileId, command(testedOn = LocalDate.of(2026, 8, 1), items = arrayOf("028" to 30)))
        val newer = service.register(actorId, profileId, command(testedOn = LocalDate.of(2026, 9, 1), items = arrayOf("028" to 40)))
        assertThat(service.latest(actorId, profileId)?.id).isEqualTo(newer.id)
    }
}
