package kr.ac.kookmin.familyfitness.fitness.application.port

import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction
import java.time.LocalDate
import java.util.UUID

interface FitnessTestRepository {
    fun save(test: FitnessTest): FitnessTest

    fun findById(id: UUID): FitnessTest?

    /** testedOn 이 가장 늦은 회차. */
    fun findLatestByProfileId(profileId: UUID): FitnessTest?

    fun existsByProfileIdAndTestedOn(
        profileId: UUID,
        testedOn: LocalDate,
    ): Boolean

    fun existsByProfileIdIn(profileIds: Collection<UUID>): Boolean
}

interface NormRepository {
    fun findAll(): List<NormPoint>
}

interface PredictionRepository {
    fun save(prediction: Prediction): Prediction
}
