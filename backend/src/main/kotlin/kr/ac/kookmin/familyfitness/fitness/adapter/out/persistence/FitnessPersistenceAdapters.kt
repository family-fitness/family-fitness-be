package kr.ac.kookmin.familyfitness.fitness.adapter.out.persistence

import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository
import kr.ac.kookmin.familyfitness.fitness.application.port.NormRepository
import kr.ac.kookmin.familyfitness.fitness.application.port.PredictionRepository
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionPoint
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Repository
@Transactional(readOnly = true)
class NormRepositoryAdapter(
    private val jpa: FitnessNormJpaRepository,
) : NormRepository {
    override fun findAll(): List<NormPoint> =
        jpa.findAll().map {
            NormPoint(
                itemCode = it.itemCode,
                sex = Sex.valueOf(it.sex),
                ageFrom = it.ageFrom,
                ageTo = it.ageTo,
                percentile = it.percentile,
                value = it.normValue.toDouble(),
                sourceYear = it.sourceYear,
            )
        }
}

@Repository
@Transactional(readOnly = true)
class FitnessTestRepositoryAdapter(
    private val jpa: FitnessTestJpaRepository,
) : FitnessTestRepository {
    @Transactional
    override fun save(test: FitnessTest): FitnessTest {
        jpa.save(test.toEntity())
        return test
    }

    override fun findById(id: UUID): FitnessTest? = jpa.findById(id).orElse(null)?.toDomain()

    override fun findLatestByProfileId(profileId: UUID): FitnessTest? = jpa.findFirstByProfileIdOrderByTestedOnDesc(profileId)?.toDomain()

    override fun existsByProfileIdAndTestedOn(
        profileId: UUID,
        testedOn: LocalDate,
    ): Boolean = jpa.existsByProfileIdAndTestedOn(profileId, testedOn)

    override fun existsByProfileIdIn(profileIds: Collection<UUID>): Boolean = jpa.existsByProfileIdIn(profileIds)

    private fun FitnessTest.toEntity() =
        FitnessTestEntity(
            id = id,
            profileId = profileId,
            testedOn = testedOn,
            source = source.name,
            ageAtTest = ageAtTest,
            heightCm = heightCm,
            weightKg = weightKg,
            createdAt = createdAt,
            items =
                items
                    .map {
                        FitnessTestItemEmbeddable(
                            itemCode = it.item.code,
                            rawValue = it.value,
                            percentile = it.score.percentile,
                            grade = it.score.grade?.label,
                            band = it.score.band?.wire,
                        )
                    }.toMutableList(),
        )

    private fun FitnessTestEntity.toDomain() =
        FitnessTest.reconstitute(
            id = id,
            profileId = profileId,
            testedOn = testedOn,
            source = FitnessTestSource.valueOf(source),
            ageAtTest = ageAtTest,
            heightCm = heightCm,
            weightKg = weightKg,
            items = items.map { FitnessTest.StoredItem(it.itemCode, it.rawValue, it.percentile) },
            createdAt = createdAt,
        )
}

@Repository
class PredictionRepositoryAdapter(
    private val jpa: PredictionJpaRepository,
) : PredictionRepository {
    @Transactional
    override fun save(prediction: Prediction): Prediction {
        jpa.save(prediction.toEntity())
        return prediction
    }

    private fun Prediction.toEntity() =
        PredictionEntity(
            id = id,
            profileId = profileId,
            fitnessTestId = fitnessTestId,
            itemCode = itemCode,
            horizonYears = horizonYears,
            modelVersion = modelVersion,
            basis = basis,
            notice = notice,
            createdAt = createdAt,
            points = points.map { it.toEmbeddable() }.toMutableList(),
        )

    private fun PredictionPoint.toEmbeddable() =
        PredictionPointEmbeddable(
            scenario = scenario.name,
            itemCode = itemCode,
            yearsFromNow = yearsFromNow,
            p10 = p10,
            p50 = p50,
            p90 = p90,
        )
}
