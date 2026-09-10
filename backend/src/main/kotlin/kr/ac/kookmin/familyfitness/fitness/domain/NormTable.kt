package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.Sex

/** 규준 구간의 나이 단위. 유아기 규준은 개월(48~83개월) 단위다. */
enum class NormAgeUnit(
    val wire: String,
) {
    YEARS("세"),
    MONTHS("개월"),
    ;

    companion object {
        fun of(wire: String): NormAgeUnit = entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("알 수 없는 나이 단위: $wire")
    }
}

/** `fitness_norms` 한 행. [value] 는 그 백분위에 해당하는 측정값(↓ 항목은 백분위가 오를수록 값이 작아진다). */
data class NormPoint(
    val itemCode: String,
    val sex: Sex,
    val ageFrom: Int,
    val ageTo: Int,
    val percentile: Int,
    val value: Double,
    val sourceYear: Int,
    val ageUnit: NormAgeUnit = NormAgeUnit.YEARS,
)

/** 한 (항목, 성별, 나이 구간)의 백분위 포인트들. 백분위 오름차순. */
data class NormBucket(
    val ageFrom: Int,
    val ageTo: Int,
    val sourceYear: Int,
    val points: List<Point>,
) {
    data class Point(
        val percentile: Int,
        val value: Double,
    )

    fun covers(age: Int): Boolean = age in ageFrom..ageTo
}

/**
 * 부팅 시 메모리에 올리는 규준표. 같은 (항목, 성별, 나이 구간)에 연도가 여럿이면 최신 `source_year` 만 쓴다.
 * 불변이라 교체(refresh)는 통째로 바꾼다.
 */
class NormTable private constructor(
    private val buckets: Map<Key, List<NormBucket>>,
) {
    private data class Key(
        val itemCode: String,
        val sex: Sex,
        val unit: NormAgeUnit,
    )

    val size: Int get() = buckets.values.sumOf { it.size }

    /**
     * 개월 구간(유아기)이 [ageMonths] 를 덮으면 그것을, 아니면 연 구간에서 [ageYears] 를 덮는 것을 고른다.
     * 겹치면 좁은 구간. 없으면 null.
     */
    fun bucket(
        itemCode: String,
        sex: Sex,
        ageYears: Int,
        ageMonths: Int = ageYears * 12,
    ): NormBucket? = bucket(Key(itemCode, sex, NormAgeUnit.MONTHS), ageMonths) ?: bucket(Key(itemCode, sex, NormAgeUnit.YEARS), ageYears)

    private fun bucket(
        key: Key,
        age: Int,
    ): NormBucket? = buckets[key]?.filter { it.covers(age) }?.minByOrNull { it.ageTo - it.ageFrom }

    companion object {
        val EMPTY = NormTable(emptyMap())

        fun of(points: Collection<NormPoint>): NormTable {
            val byBucket =
                points
                    .groupBy { Key(it.itemCode, it.sex, it.ageUnit) }
                    .mapValues { (_, rows) ->
                        rows
                            .groupBy { it.ageFrom to it.ageTo }
                            .map { (range, candidates) ->
                                val latestYear = candidates.maxOf { it.sourceYear }
                                NormBucket(
                                    ageFrom = range.first,
                                    ageTo = range.second,
                                    sourceYear = latestYear,
                                    points =
                                        candidates
                                            .filter { it.sourceYear == latestYear }
                                            .sortedBy { it.percentile }
                                            .map { NormBucket.Point(it.percentile, it.value) },
                                )
                            }.sortedBy { it.ageFrom }
                    }
            return NormTable(byBucket)
        }
    }
}
