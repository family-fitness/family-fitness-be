package kr.ac.kookmin.familyfitness.fitness.application

import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository
import kr.ac.kookmin.familyfitness.fitness.domain.CoachDirection
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.shared.domain.Copy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/** 구성원 한 명의 카드. [latest] 가 null 이면 "첫 측정을 등록하면 지도가 그려져요", [ProfileSummary.measurable] 이 false 면 측정 버튼 없음. */
data class FitnessMapMember(
    val profile: ProfileSummary,
    /** 예: `유소년 상위 37%`. 측정이 없으면 null. */
    val headline: String?,
    val latest: FitnessMapLatest?,
)

data class FitnessMapLatest(
    val fitnessTestId: UUID,
    val testedOn: LocalDate,
    /** 측정 항목 백분위 평균(1~99). 규준이 없는 항목만 있으면 null. */
    val overallPercentile: Int?,
    val weakest: kr.ac.kookmin.familyfitness.fitness.api.FactorPoint?,
    val strongest: kr.ac.kookmin.familyfitness.fitness.api.FactorPoint?,
    val coachDirection: CoachDirection,
)

data class FitnessMap(
    val familyId: UUID,
    val familyName: String?,
    val members: List<FitnessMapMember>,
)

/**
 * 홈(`/home` 가족 체력 지도 ★메인) 한 번의 조회. 구성원 카드 = 프로필 요약 + 최신 측정 요약.
 * 가족 체력 지도는 비교·순위가 아니다 — 구성원 사이 정렬은 프로필 순서 그대로다.
 */
@Service
class FitnessMapService(
    private val tests: FitnessTestRepository,
    private val familyAccess: FamilyAccess,
    private val profileQuery: ProfileQuery,
) {
    @Transactional(readOnly = true)
    fun of(
        actorId: UUID,
        familyId: UUID,
    ): FitnessMap {
        familyAccess.requireMember(actorId, familyId)
        val members =
            profileQuery.summariesOfFamily(familyId).map { profile ->
                val latest = tests.findLatestByProfileId(profile.profileId)
                FitnessMapMember(profile, latest?.let { headline(profile, it) }, latest?.let(::summarize))
            }
        return FitnessMap(familyId, profileQuery.familyName(familyId), members)
    }

    private fun summarize(test: FitnessTest) =
        FitnessMapLatest(
            fitnessTestId = test.id,
            testedOn = test.testedOn,
            overallPercentile = test.overallPercentile,
            weakest = test.weakest,
            strongest = test.strongest,
            coachDirection = test.coachDirection,
        )

    private fun headline(
        profile: ProfileSummary,
        test: FitnessTest,
    ): String? = test.overallPercentile?.let { "${profile.ageGroup.label} ${Copy.topPercentText(it)}" }
}
