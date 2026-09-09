package kr.ac.kookmin.familyfitness.fitness.domain

import com.fasterxml.jackson.annotation.JsonValue
import kr.ac.kookmin.familyfitness.shared.domain.Band
import kr.ac.kookmin.familyfitness.shared.domain.Copy

/** 등급. 와이어 값은 한글 라벨. 백분위→등급 임계값 ▲ 확정 필요 (잠정 1등급≥90 · 2등급≥75 · 3등급≥50 · 그 외 참가). */
enum class Grade(
    @get:JsonValue val label: String,
) {
    FIRST("1등급"),
    SECOND("2등급"),
    THIRD("3등급"),
    PARTICIPATION("참가"),
    ;

    companion object {
        const val FIRST_FROM = 90
        const val SECOND_FROM = 75
        const val THIRD_FROM = 50

        fun ofPercentile(percentile: Int): Grade =
            when {
                percentile >= FIRST_FROM -> FIRST
                percentile >= SECOND_FROM -> SECOND
                percentile >= THIRD_FROM -> THIRD
                else -> PARTICIPATION
            }

        fun fromLabel(label: String): Grade =
            entries.firstOrNull { it.label == label || it.name == label }
                ?: throw IllegalArgumentException("알 수 없는 등급: $label")
    }
}

/**
 * 백분위에서 파생되는 값 묶음. 등급·구간·「상위 n%」 문구는 여기 한 곳에서만 계산한다.
 * 규준이 없어 백분위가 null 이면 나머지도 전부 null 이다.
 */
data class ItemScore(
    val percentile: Int?,
    val grade: Grade?,
    val band: Band?,
    val topPercentText: String?,
) {
    companion object {
        val NONE = ItemScore(null, null, null, null)

        fun ofPercentile(percentile: Int?): ItemScore =
            if (percentile == null) {
                NONE
            } else {
                ItemScore(
                    percentile = percentile,
                    grade = Grade.ofPercentile(percentile),
                    band = Band.ofPercentile(percentile),
                    topPercentText = Copy.topPercentText(percentile),
                )
            }
    }
}
