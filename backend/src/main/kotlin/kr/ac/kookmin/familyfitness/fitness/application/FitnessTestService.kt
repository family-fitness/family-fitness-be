package kr.ac.kookmin.familyfitness.fitness.application

import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository
import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException
import kr.ac.kookmin.familyfitness.fitness.domain.DuplicateDateException
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource
import kr.ac.kookmin.familyfitness.fitness.domain.FutureTestDateException
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement
import kr.ac.kookmin.familyfitness.fitness.domain.NotMeasurableException
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.shared.domain.Ages
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class RegisterFitnessTestCommand(
    val testedOn: LocalDate,
    val source: FitnessTestSource,
    val heightCm: BigDecimal?,
    val weightKg: BigDecimal?,
    val measurements: List<Measurement>,
)

/**
 * 측정 회차 등록·조회. 호출 계정(actor)은 대상 프로필과 같은 가족이어야 한다 — 부모가 아이 기록을 대리 입력한다.
 * 규칙 순서: 같은 가족 → 미래 날짜 → 만 4세 미만 → 보호자 동의 → 같은 날짜 중복 → 항목 규칙(애그리거트).
 */
@Service
class FitnessTestService(
    private val tests: FitnessTestRepository,
    private val norms: NormCatalog,
    private val familyAccess: FamilyAccess,
    private val profileQuery: ProfileQuery,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    @Transactional
    fun register(
        actorId: UUID,
        profileId: UUID,
        command: RegisterFitnessTestCommand,
    ): FitnessTest {
        val summary = familyAccess.requireSameFamilyAsProfile(actorId, profileId)
        val details = profileQuery.findDetails(profileId) ?: throw ProfileNotFoundException(profileId)

        val today = LocalDate.now(clock.withZone(zone))
        if (command.testedOn.isAfter(today)) throw FutureTestDateException(command.testedOn)

        val ageAtTest = Ages.fullYears(details.birthDate, command.testedOn)
        if (ageAtTest < Ages.MEASURABLE_FROM_YEARS) throw NotMeasurableException()
        requireConsent(summary)

        if (tests.existsByProfileIdAndTestedOn(profileId, command.testedOn)) throw DuplicateDateException(command.testedOn)

        val calculator = norms.calculator()
        val test =
            FitnessTest.register(
                id = UUID.randomUUID(),
                profileId = profileId,
                testedOn = command.testedOn,
                source = command.source,
                ageAtTest = ageAtTest,
                heightCm = command.heightCm,
                weightKg = command.weightKg,
                measurements = command.measurements,
                scorer = { item, value -> calculator.percentile(item, details.sex, ageAtTest, value.toDouble()) },
                createdAt = clock.instant(),
            )
        return tests.save(test)
    }

    /** 최신 회차. 이력이 없으면 null — 웹 어댑터가 빈 응답(200)으로 바꾼다. */
    @Transactional(readOnly = true)
    fun latest(
        actorId: UUID,
        profileId: UUID,
    ): FitnessTest? {
        familyAccess.requireSameFamilyAsProfile(actorId, profileId)
        return tests.findLatestByProfileId(profileId)
    }

    /** 만 14세 미만(consentRequired)인데 동의가 없거나 철회됐으면 저장하지 않는다. */
    private fun requireConsent(summary: ProfileSummary) {
        if (summary.consentRequired && !summary.consentGiven) throw ConsentRequiredException()
    }
}
