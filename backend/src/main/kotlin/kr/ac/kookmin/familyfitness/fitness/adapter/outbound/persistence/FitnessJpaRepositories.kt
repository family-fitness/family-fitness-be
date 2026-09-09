package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface FitnessNormJpaRepository : JpaRepository<FitnessNormEntity, Long>

interface FitnessTestJpaRepository : JpaRepository<FitnessTestEntity, UUID> {
    fun findFirstByProfileIdOrderByTestedOnDesc(profileId: UUID): FitnessTestEntity?

    fun existsByProfileIdAndTestedOn(
        profileId: UUID,
        testedOn: LocalDate,
    ): Boolean

    fun existsByProfileIdIn(profileIds: Collection<UUID>): Boolean
}

interface PredictionJpaRepository : JpaRepository<PredictionEntity, UUID>
