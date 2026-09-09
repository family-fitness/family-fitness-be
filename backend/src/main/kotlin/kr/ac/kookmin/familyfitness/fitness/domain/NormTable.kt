package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.Sex

/** `fitness_norms` 한 행. [value] 는 그 백분위에 해당하는 측정값(↓ 항목은 백분위가 오를수록 값이 작아진다). */
data class NormPoint(
    val itemCode: String,
    val sex: Sex,
    val ageFrom: Int,
    val ageTo: Int,
    val percentile: Int,
    val value: Double,
    val sourceYear: Int,
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
    )

    val size: Int get() = buckets.values.sumOf { it.size }

    /** age_from ≤ age ≤ age_to 인 구간. 겹치면 좁은 구간을 고른다. 없으면 null. */
    fun bucket(
        itemCode: String,
        sex: Sex,
        age: Int,
    ): NormBucket? =
        buckets[Key(itemCode, sex)]
            ?.filter { it.covers(age) }
            ?.minByOrNull { it.ageTo - it.ageFrom }

    companion object {
        val EMPTY = NormTable(emptyMap())

        fun of(points: Collection<NormPoint>): NormTable {
            val byBucket =
                points
                    .groupBy { Key(it.itemCode, it.sex) }
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
