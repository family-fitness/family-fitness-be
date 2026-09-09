package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

enum class FitnessTestSource {
    SELF_INPUT,
    CENTER_SHEET,
}

/** 결과 화면의 코치 방향. 가장 낮은 항목의 백분위가 75 를 넘으면 강화, 아니면 성장. */
enum class CoachDirection {
    STRENGTHEN,
    GROWTH,
}

/** 입력 한 줄. 코드는 아직 검증 전이다. */
data class Measurement(
    val itemCode: String,
    val value: BigDecimal,
)

/** 저장된 항목 한 줄. 백분위는 저장 시점 값으로 굳고, 등급·구간은 그 백분위에서 파생된다. */
data class FitnessTestItem(
    val item: FitnessItem,
    val value: BigDecimal,
    val score: ItemScore,
) {
    val percentile: Int? get() = score.percentile
}

data class RadarPoint(
    val factor: FitnessFactor,
    val percentile: Int?,
)

/**
 * 측정 회차 애그리거트. 항목과 함께 통째로 저장되고, 저장 뒤에는 바뀌지 않는다.
 * 규칙: 항목 0개 저장 안 함 · 005/006 거부 · 카탈로그 밖 코드 거부 · 연령대 항목만 허용 · 한 요청에 같은 항목 두 번 금지.
 */
class FitnessTest private constructor(
    val id: UUID,
    val profileId: UUID,
    val testedOn: LocalDate,
    val source: FitnessTestSource,
    /** testedOn 기준 만 나이 */
    val ageAtTest: Int,
    val heightCm: BigDecimal?,
    val weightKg: BigDecimal?,
    val items: List<FitnessTestItem>,
    val createdAt: Instant,
) {
    val ageGroup: AgeGroup = AgeGroup.ofAge(ageAtTest)

    /** itemCode → 원시값. 005·006 은 애초에 없다. */
    val measurements: Map<String, BigDecimal> get() = items.associate { it.item.code to it.value }

    private val scoredItems: List<FitnessTestItem> get() = items.filter { it.percentile != null }

    /** 레이더 5요인. 요인에 항목이 여럿이면 백분위 평균(반올림), 하나도 없으면 null. */
    fun radar(): List<RadarPoint> =
        FitnessFactor.RADAR.map { factor ->
            val percentiles = scoredItems.filter { it.item.factor == factor }.map { it.percentile!! }
            RadarPoint(factor, percentiles.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
        }

    val weakest: FactorPoint? get() = scoredItems.minByOrNull { it.percentile!! }?.toFactorPoint()

    val strongest: FactorPoint? get() = scoredItems.maxByOrNull { it.percentile!! }?.toFactorPoint()

    val coachDirection: CoachDirection
        get() =
            if ((weakest?.percentile ?: 0) > STRENGTHEN_ABOVE) CoachDirection.STRENGTHEN else CoachDirection.GROWTH

    private fun FitnessTestItem.toFactorPoint() = FactorPoint(item.factor, item.code, percentile!!)

    companion object {
        const val STRENGTHEN_ABOVE = 75

        /**
         * 새 측정 회차. [scorer] 가 (항목, 값) → 백분위(규준 없으면 null) 를 돌려주고, 그 결과가 저장 시점 값으로 굳는다.
         */
        fun register(
            id: UUID,
            profileId: UUID,
            testedOn: LocalDate,
            source: FitnessTestSource,
            ageAtTest: Int,
            heightCm: BigDecimal?,
            weightKg: BigDecimal?,
            measurements: List<Measurement>,
            scorer: (FitnessItem, BigDecimal) -> Int?,
            createdAt: Instant,
        ): FitnessTest {
            if (measurements.isEmpty()) throw NoItemsException()
            val ageGroup = AgeGroup.ofAge(ageAtTest)
            val seen = mutableSetOf<FitnessItem>()
            val items =
                measurements.map { m ->
                    val item = FitnessItem.resolve(m.itemCode)
                    if (!seen.add(item)) throw DuplicateItemException(item.code)
                    if (!item.isFor(ageGroup)) throw ItemNotForAgeGroupException(item.code, ageGroup)
                    FitnessTestItem(item, m.value, ItemScore.ofPercentile(scorer(item, m.value)))
                }
            return FitnessTest(id, profileId, testedOn, source, ageAtTest, heightCm, weightKg, items, createdAt)
        }

        /** 저장소에서 복원. 굳어 있는 백분위에서 등급·구간을 다시 파생한다(계산은 [ItemScore] 한 곳). */
        fun reconstitute(
            id: UUID,
            profileId: UUID,
            testedOn: LocalDate,
            source: FitnessTestSource,
            ageAtTest: Int,
            heightCm: BigDecimal?,
            weightKg: BigDecimal?,
            items: List<StoredItem>,
            createdAt: Instant,
        ): FitnessTest =
            FitnessTest(
                id = id,
                profileId = profileId,
                testedOn = testedOn,
                source = source,
                ageAtTest = ageAtTest,
                heightCm = heightCm,
                weightKg = weightKg,
                items =
                    items.map {
                        FitnessTestItem(
                            item = FitnessItem.findByCode(it.itemCode) ?: throw UnknownItemException(it.itemCode),
                            value = it.value,
                            score = ItemScore.ofPercentile(it.percentile),
                        )
                    },
                createdAt = createdAt,
            )
    }

    data class StoredItem(
        val itemCode: String,
        val value: BigDecimal,
        val percentile: Int?,
    )
}
