package kr.ac.kookmin.familyfitness.fitness.application

import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 다른 모듈(coaching)이 읽는 최신 측정 요약. 권한 판단은 호출 모듈의 책임이다. */
@Service
class FitnessQueryService(
    private val tests: FitnessTestRepository,
) : FitnessQuery {
    @Transactional(readOnly = true)
    override fun latestOf(profileId: UUID): LatestFitness? =
        tests.findLatestByProfileId(profileId)?.let {
            LatestFitness(
                profileId = it.profileId,
                fitnessTestId = it.id,
                testedOn = it.testedOn,
                heightCm = it.heightCm,
                weightKg = it.weightKg,
                measurements = it.measurements,
                weakest = it.weakest,
                strongest = it.strongest,
            )
        }

    @Transactional(readOnly = true)
    override fun hasAnyTest(profileIds: Collection<UUID>): Boolean = profileIds.isNotEmpty() && tests.existsByProfileIdIn(profileIds)
}
