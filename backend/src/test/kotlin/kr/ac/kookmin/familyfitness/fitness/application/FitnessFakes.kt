package kr.ac.kookmin.familyfitness.fitness.application

import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository
import kr.ac.kookmin.familyfitness.fitness.application.port.NormRepository
import kr.ac.kookmin.familyfitness.fitness.application.port.PredictionRepository
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class InMemoryFitnessTestRepository : FitnessTestRepository {
    val saved = mutableMapOf<UUID, FitnessTest>()

    override fun save(test: FitnessTest): FitnessTest {
        saved[test.id] = test
        return test
    }

    override fun findById(id: UUID): FitnessTest? = saved[id]

    override fun findLatestByProfileId(profileId: UUID): FitnessTest? =
        saved.values.filter { it.profileId == profileId }.maxByOrNull { it.testedOn }

    override fun existsByProfileIdAndTestedOn(
        profileId: UUID,
        testedOn: LocalDate,
    ): Boolean = saved.values.any { it.profileId == profileId && it.testedOn == testedOn }

    override fun existsByProfileIdIn(profileIds: Collection<UUID>): Boolean = saved.values.any { it.profileId in profileIds }
}

class InMemoryPredictionRepository : PredictionRepository {
    val saved = mutableListOf<Prediction>()

    override fun save(prediction: Prediction): Prediction {
        saved += prediction
        return prediction
    }
}

class StaticNormRepository(
    private val points: List<NormPoint>,
) : NormRepository {
    override fun findAll(): List<NormPoint> = points
}

/** 규준 한 구간을 짧게 만든다. */
fun norms(
    itemCode: String,
    sex: Sex,
    ageFrom: Int,
    ageTo: Int,
    vararg pairs: Pair<Int, Double>,
): List<NormPoint> = pairs.map { (p, v) -> NormPoint(itemCode, sex, ageFrom, ageTo, p, v, 1900) }

fun summaryOf(
    profileId: UUID,
    familyId: UUID,
    ageGroup: AgeGroup,
    measurable: Boolean = true,
    consentRequired: Boolean = true,
    consentGiven: Boolean = true,
) = ProfileSummary(
    profileId = profileId,
    familyId = familyId,
    name = "아이",
    role = ProfileRole.CHILD,
    ageGroup = ageGroup,
    hasAccount = false,
    inviteStatus = InviteStatus.NONE,
    supportMode = null,
    measurable = measurable,
    consentRequired = consentRequired,
    consentGiven = consentGiven,
)

fun detailsOf(
    profileId: UUID,
    familyId: UUID,
    birthDate: LocalDate,
    sex: Sex = Sex.F,
    heightCm: BigDecimal? = null,
    weightKg: BigDecimal? = null,
    consentGiven: Boolean = true,
) = ProfileDetails(
    profileId = profileId,
    familyId = familyId,
    userId = null,
    name = "아이",
    role = ProfileRole.CHILD,
    birthDate = birthDate,
    sex = sex,
    heightCm = heightCm,
    weightKg = weightKg,
    supportMode = null,
    consentGiven = consentGiven,
)
