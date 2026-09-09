package kr.ac.kookmin.familyfitness.fitness.application

import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository
import kr.ac.kookmin.familyfitness.fitness.application.port.PredictionRepository
import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessItem
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestNotFoundException
import kr.ac.kookmin.familyfitness.fitness.domain.NoFitnessTestException
import kr.ac.kookmin.familyfitness.fitness.domain.NotMeasurableException
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionPoint
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionScenario
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryRequest
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Ages
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PredictCommand(
    /** 생략하면 최신 회차 */
    val fitnessTestId: UUID? = null,
    val horizonYears: Int = DEFAULT_HORIZON_YEARS,
    val itemCode: String = DEFAULT_ITEM_CODE,
) {
    companion object {
        const val DEFAULT_HORIZON_YEARS = 10
        const val DEFAULT_ITEM_CODE = "028"
    }
}

/**
 * `AiGateway.trajectory` 를 불러 결과를 그대로 굳힌다. AI 장애([kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException])는
 * 잡지 않고 그대로 올린다(503). 외부 호출을 트랜잭션 안에 두지 않으려고 저장은 저장소 어댑터의 트랜잭션에 맡긴다.
 */
@Service
class PredictionService(
    private val tests: FitnessTestRepository,
    private val predictions: PredictionRepository,
    private val ai: AiGateway,
    private val familyAccess: FamilyAccess,
    private val profileQuery: ProfileQuery,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    fun predict(
        actorId: UUID,
        profileId: UUID,
        command: PredictCommand,
    ): Prediction {
        val summary = familyAccess.requireSameFamilyAsProfile(actorId, profileId)
        val details = profileQuery.findDetails(profileId) ?: throw ProfileNotFoundException(profileId)
        if (summary.consentRequired && !summary.consentGiven) throw ConsentRequiredException()
        if (!summary.measurable) throw NotMeasurableException()

        val item = FitnessItem.resolve(command.itemCode)
        val test = baseTest(profileId, command.fitnessTestId)

        val today = LocalDate.now(clock.withZone(zone))
        val currentAgeYears = Ages.fullYears(details.birthDate, today)
        val profile = aiProfile(profileId, details, test, today, currentAgeYears)

        val response = ai.trajectory(TrajectoryRequest(profile, item.code, command.horizonYears))

        // ▲ 확정 필요 — AI bands[].age 는 만 나이(세)로 본다. 지금 나이보다 어린 구간은 버린다.
        val points =
            response.bands
                .filter { it.age >= currentAgeYears }
                .map {
                    PredictionPoint(
                        scenario = PredictionScenario.MAINTAIN,
                        itemCode = response.itemCode,
                        yearsFromNow = it.age - currentAgeYears,
                        p10 = it.p10?.let(BigDecimal::valueOf),
                        p50 = it.p50?.let(BigDecimal::valueOf),
                        p90 = it.p90?.let(BigDecimal::valueOf),
                    )
                }.distinctBy { it.yearsFromNow }

        val prediction =
            Prediction(
                id = UUID.randomUUID(),
                profileId = profileId,
                fitnessTestId = test.id,
                itemCode = item.code,
                horizonYears = command.horizonYears,
                modelVersion = Prediction.MODEL_VERSION,
                basis = response.basis,
                notice = response.notice,
                points = points,
                createdAt = clock.instant(),
            )
        return predictions.save(prediction)
    }

    private fun baseTest(
        profileId: UUID,
        fitnessTestId: UUID?,
    ): FitnessTest =
        if (fitnessTestId == null) {
            tests.findLatestByProfileId(profileId) ?: throw NoFitnessTestException()
        } else {
            tests
                .findById(fitnessTestId)
                ?.takeIf { it.profileId == profileId }
                ?: throw FitnessTestNotFoundException(fitnessTestId)
        }

    /** 이름·생년월일·계정 식별자는 보내지 않는다. 유아기는 개월, 그 외는 세. 측정값은 회차 항목(005·006 은 애초에 없다). */
    private fun aiProfile(
        profileId: UUID,
        details: ProfileDetails,
        test: FitnessTest,
        today: LocalDate,
        currentAgeYears: Int,
    ): AiProfile {
        val ageGroup = AgeGroup.ofAge(currentAgeYears)
        val age = if (ageGroup == AgeGroup.TODDLER) Ages.fullMonths(details.birthDate, today) else currentAgeYears
        return AiProfile(
            profileRef = ProfileRef.of(profileId),
            age = age,
            ageUnit = ageGroup.ageUnit,
            sex = details.sex.name,
            heightCm = (test.heightCm ?: details.heightCm)?.toDouble(),
            weightKg = (test.weightKg ?: details.weightKg)?.toDouble(),
            measurements = test.measurements.mapValues { it.value.toDouble() },
        )
    }
}
