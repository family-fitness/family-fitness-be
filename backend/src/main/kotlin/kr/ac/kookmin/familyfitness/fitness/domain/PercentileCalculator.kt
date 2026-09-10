package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kotlin.math.roundToInt

/**
 * 측정값 → 백분위 (계약 §0 「백분위·등급 계산」).
 * - (항목, 성별, 나이) 구간의 백분위 포인트 사이를 선형 보간하고 표 밖은 끝점으로 자른다.
 * - ↓(낮을수록 좋은) 항목은 부호를 뒤집어 계산한다 — 값이 작을수록 백분위가 높다.
 * - 결과는 정수 1~99 로 자른다(0·100 금지). 규준이 없으면 null.
 */
class PercentileCalculator(
    private val table: NormTable,
) {
    fun percentile(
        item: FitnessItem,
        sex: Sex,
        age: Int,
        value: Double,
        ageMonths: Int = age * 12,
    ): Int? {
        val bucket = table.bucket(item.code, sex, age, ageMonths) ?: return null
        if (bucket.points.isEmpty()) return null
        val raw = interpolate(bucket.points, value, item.higherIsBetter)
        return raw.roundToInt().coerceIn(MIN, MAX)
    }

    private fun interpolate(
        points: List<NormBucket.Point>,
        value: Double,
        higherIsBetter: Boolean,
    ): Double {
        val sign = if (higherIsBetter) 1.0 else -1.0
        val x = value * sign
        val oriented = points.map { it.percentile.toDouble() to it.value * sign }
        val (firstP, firstV) = oriented.first()
        val (lastP, lastV) = oriented.last()
        if (x <= firstV) return firstP
        if (x >= lastV) return lastP
        for (i in 0 until oriented.size - 1) {
            val (p0, v0) = oriented[i]
            val (p1, v1) = oriented[i + 1]
            if (x in v0..v1) {
                if (v1 == v0) return p0
                return p0 + (x - v0) / (v1 - v0) * (p1 - p0)
            }
        }
        return lastP
    }

    companion object {
        const val MIN = 1
        const val MAX = 99
    }
}
