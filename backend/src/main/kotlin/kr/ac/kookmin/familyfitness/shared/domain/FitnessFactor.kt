package kr.ac.kookmin.familyfitness.shared.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** 체력 요인. 와이어 값은 한글 라벨. 레이더 차트 5요인은 [RADAR] 순서를 따른다. */
enum class FitnessFactor(
    @get:JsonValue val label: String,
) {
    CARDIO("심폐지구력"),
    STRENGTH("근력"),
    MUSCULAR_ENDURANCE("근지구력"),
    FLEXIBILITY("유연성"),
    AGILITY("민첩성"),
    POWER("순발력"),
    COORDINATION("협응력"),
    BALANCE("평형성"),
    ;

    companion object {
        /** 결과 화면 레이더 차트 — 근력 · 근지구력 · 유연성 · 심폐지구력 · 순발력 */
        val RADAR: List<FitnessFactor> = listOf(STRENGTH, MUSCULAR_ENDURANCE, FLEXIBILITY, CARDIO, POWER)

        @JvmStatic
        @JsonCreator
        fun fromLabel(label: String): FitnessFactor =
            entries.firstOrNull { it.label == label || it.name == label }
                ?: throw IllegalArgumentException("알 수 없는 체력 요인: $label")
    }
}
