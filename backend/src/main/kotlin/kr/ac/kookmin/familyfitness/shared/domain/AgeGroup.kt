package kr.ac.kookmin.familyfitness.shared.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate
import java.time.Period

/**
 * 국민체력100 연령대. 와이어 값은 한글 라벨(`유아기` 등)이며 생년월일에서 파생한다.
 * 만 4세 미만도 [TODDLER] 로 분류하되 규준이 없어 측정 대상이 아니다 ([Ages.isMeasurable]).
 */
enum class AgeGroup(
    @get:JsonValue val label: String,
) {
    TODDLER("유아기"),
    YOUTH("유소년"),
    ADOLESCENT("청소년"),
    ADULT("성인"),
    SENIOR("어르신"),
    ;

    /** AI 서비스 `age_unit`. 유아기만 개월 단위를 쓴다. */
    val ageUnit: String get() = if (this == TODDLER) "개월" else "세"

    companion object {
        fun ofAge(fullYears: Int): AgeGroup =
            when {
                fullYears < 7 -> TODDLER
                fullYears < 13 -> YOUTH
                fullYears < 19 -> ADOLESCENT
                fullYears < 65 -> ADULT
                else -> SENIOR
            }

        fun of(
            birthDate: LocalDate,
            on: LocalDate,
        ): AgeGroup = ofAge(Ages.fullYears(birthDate, on))

        @JvmStatic
        @JsonCreator
        fun fromLabel(label: String): AgeGroup =
            entries.firstOrNull { it.label == label || it.name == label }
                ?: throw IllegalArgumentException("알 수 없는 연령대: $label")
    }
}

/** 만 나이·개월 계산과 연령 규칙을 한곳에 둔다. */
object Ages {
    const val MEASURABLE_FROM_YEARS = 4
    const val GUARDIAN_CONSENT_UNDER_YEARS = 14

    fun fullYears(
        birthDate: LocalDate,
        on: LocalDate,
    ): Int = Period.between(birthDate, on).years.coerceAtLeast(0)

    fun fullMonths(
        birthDate: LocalDate,
        on: LocalDate,
    ): Int =
        Period
            .between(birthDate, on)
            .toTotalMonths()
            .toInt()
            .coerceAtLeast(0)

    /** 만 4세 미만은 규준이 없어 측정 대상이 아니다. */
    fun isMeasurable(
        birthDate: LocalDate,
        on: LocalDate,
    ): Boolean = fullYears(birthDate, on) >= MEASURABLE_FROM_YEARS

    /** 만 14세 미만은 보호자 동의가 있어야 저장된다. */
    fun requiresGuardianConsent(
        birthDate: LocalDate,
        on: LocalDate,
    ): Boolean = fullYears(birthDate, on) < GUARDIAN_CONSENT_UNDER_YEARS
}
