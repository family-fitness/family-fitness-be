package kr.ac.kookmin.familyfitness.shared.domain

import com.fasterxml.jackson.annotation.JsonValue

/**
 * 백분위 구간. 와이어 값은 소문자(`strength` 등). 화면에 코드값을 그대로 쓰지 않고 [copy] 문구로 바꾼다.
 * 「부족」·「미달」·「하위」는 쓰지 않는다.
 */
enum class Band(
    @get:JsonValue val wire: String,
    val copy: String,
) {
    STRENGTH("strength", "잘하고 있는 영역"),
    STEADY("steady", "꾸준히 하고 있는 영역"),
    GROWTH("growth", "지금 키우기 좋은 영역"),
    ;

    companion object {
        const val STRENGTH_FROM = 75
        const val GROWTH_BELOW = 25

        fun ofPercentile(percentile: Int): Band =
            when {
                percentile >= STRENGTH_FROM -> STRENGTH
                percentile < GROWTH_BELOW -> GROWTH
                else -> STEADY
            }
    }
}
