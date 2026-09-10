package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor

/** EASY = 장비 없이 집에서 되는 항목(필수) · EQUIPMENT = 장비·공간이 필요한 항목(선택). */
enum class InputGroup {
    EASY,
    EQUIPMENT,
}

/** 프론트 검증용 잠정 범위. 서버는 이 범위로 거부하지 않는다. */
data class ValueRange(
    val min: Int,
    val max: Int,
)

private const val SPACE = "공간"
private const val DEVICE = "장비"

/**
 * 측정 항목 카탈로그 (계약 §0 · AI 명세 §9 · `family-fitness-ai/stats/items.py`).
 * 코드가 식별자, 이름은 표기. 신체조성(003·004·018·042)과 혈압(005·006)은 카탈로그에 없다.
 */
enum class FitnessItem(
    val code: String,
    val itemName: String,
    val unit: String,
    val factor: FitnessFactor,
    val higherIsBetter: Boolean,
    val inputGroup: InputGroup,
    /** 필요한 장비·공간. EASY 항목은 null. ▲ 확정 필요 — 세부 장비명은 계약에 없어 「공간」·「장비」로만 적는다. */
    val equipment: String?,
    val range: ValueRange,
    private val ageGroups: Set<AgeGroup>,
) {
    SIT_UP(
        "009",
        "윗몸말아올리기",
        "회",
        FitnessFactor.MUSCULAR_ENDURANCE,
        true,
        InputGroup.EASY,
        null,
        ValueRange(0, 120),
        setOf(AgeGroup.TODDLER, AgeGroup.YOUTH, AgeGroup.ADOLESCENT),
    ),
    REPEATED_JUMP(
        "010",
        "반복점프",
        "회",
        FitnessFactor.MUSCULAR_ENDURANCE,
        true,
        InputGroup.EASY,
        null,
        ValueRange(0, 120),
        setOf(AgeGroup.ADOLESCENT),
    ),
    SIT_AND_REACH(
        "012",
        "앉아윗몸앞으로굽히기",
        "cm",
        FitnessFactor.FLEXIBILITY,
        true,
        InputGroup.EASY,
        null,
        ValueRange(-30, 40),
        AgeGroup.entries.toSet(),
    ),
    ILLINOIS(
        "013",
        "일리노이",
        "초",
        FitnessFactor.AGILITY,
        false,
        InputGroup.EQUIPMENT,
        SPACE,
        ValueRange(5, 60),
        setOf(AgeGroup.ADOLESCENT),
    ),
    FLIGHT_TIME(
        "014",
        "체공시간",
        "초",
        FitnessFactor.POWER,
        true,
        InputGroup.EASY,
        null,
        ValueRange(0, 2),
        setOf(AgeGroup.ADOLESCENT),
    ),
    EYE_HAND_COORDINATION(
        "017",
        "눈-손협응력",
        "초",
        FitnessFactor.COORDINATION,
        false,
        InputGroup.EQUIPMENT,
        DEVICE,
        ValueRange(0, 120),
        setOf(AgeGroup.ADOLESCENT),
    ),
    CROSS_SIT_UP(
        "019",
        "교차윗몸일으키기",
        "회",
        FitnessFactor.MUSCULAR_ENDURANCE,
        true,
        InputGroup.EASY,
        null,
        ValueRange(0, 120),
        setOf(AgeGroup.ADULT, AgeGroup.SENIOR),
    ),
    SHUTTLE_RUN(
        "020",
        "왕복오래달리기",
        "회",
        FitnessFactor.CARDIO,
        true,
        InputGroup.EQUIPMENT,
        SPACE,
        ValueRange(0, 150),
        setOf(AgeGroup.TODDLER, AgeGroup.YOUTH, AgeGroup.ADOLESCENT, AgeGroup.ADULT),
    ),
    SHUTTLE_RUN_10M_X4(
        "021",
        "10m4회왕복달리기",
        "초",
        FitnessFactor.AGILITY,
        false,
        InputGroup.EQUIPMENT,
        SPACE,
        ValueRange(5, 60),
        setOf(AgeGroup.ADULT),
    ),
    STANDING_LONG_JUMP(
        "022",
        "제자리멀리뛰기",
        "cm",
        FitnessFactor.POWER,
        true,
        InputGroup.EQUIPMENT,
        SPACE,
        ValueRange(0, 350),
        setOf(AgeGroup.TODDLER, AgeGroup.YOUTH, AgeGroup.ADULT),
    ),
    RELATIVE_GRIP(
        "028",
        "상대악력",
        "%",
        FitnessFactor.STRENGTH,
        true,
        InputGroup.EQUIPMENT,
        "악력계",
        ValueRange(0, 150),
        AgeGroup.entries.toSet(),
    ),
    TREADMILL_VO2MAX(
        "035",
        "트레드밀VO2max",
        "ml/kg/min",
        FitnessFactor.CARDIO,
        true,
        InputGroup.EQUIPMENT,
        DEVICE,
        ValueRange(10, 90),
        setOf(AgeGroup.ADOLESCENT, AgeGroup.ADULT),
    ),
    STEP_VO2MAX(
        "037",
        "스텝검사VO2max",
        "ml/kg/min",
        FitnessFactor.CARDIO,
        true,
        InputGroup.EQUIPMENT,
        DEVICE,
        ValueRange(10, 90),
        setOf(AgeGroup.ADOLESCENT, AgeGroup.ADULT),
    ),
    REACTION_TIME(
        "040",
        "반응시간",
        "초",
        FitnessFactor.AGILITY,
        false,
        InputGroup.EQUIPMENT,
        DEVICE,
        ValueRange(0, 5),
        setOf(AgeGroup.ADULT),
    ),
    ADULT_FLIGHT_TIME(
        "041",
        "성인체공시간",
        "초",
        FitnessFactor.POWER,
        true,
        InputGroup.EASY,
        null,
        ValueRange(0, 2),
        setOf(AgeGroup.ADULT),
    ),
    SIDE_STEP(
        "043",
        "반복옆뛰기",
        "회",
        FitnessFactor.AGILITY,
        true,
        InputGroup.EASY,
        null,
        ValueRange(0, 120),
        setOf(AgeGroup.YOUTH),
    ),
    SHUTTLE_RUN_5M_X4(
        "050",
        "5m4회왕복달리기",
        "초",
        FitnessFactor.AGILITY,
        false,
        InputGroup.EQUIPMENT,
        SPACE,
        ValueRange(5, 60),
        setOf(AgeGroup.TODDLER),
    ),
    BUTTON_PRESS_3X3(
        "051",
        "3x3버튼누르기",
        "초",
        FitnessFactor.COORDINATION,
        false,
        InputGroup.EQUIPMENT,
        DEVICE,
        ValueRange(0, 60),
        setOf(AgeGroup.TODDLER),
    ),
    ;

    /** EQUIPMENT 항목은 선택 입력이다. */
    val optional: Boolean get() = inputGroup == InputGroup.EQUIPMENT

    fun isFor(ageGroup: AgeGroup): Boolean = ageGroup in ageGroups

    /** 연령대별 표기. 020 은 왕복 거리가 연령대마다 달라 라벨이 갈린다. */
    fun label(ageGroup: AgeGroup): String =
        when (this) {
            SHUTTLE_RUN -> {
                when (ageGroup) {
                    AgeGroup.TODDLER -> "10m 왕복오래달리기"
                    AgeGroup.YOUTH -> "15m 왕복오래달리기"
                    else -> "20m 왕복오래달리기"
                }
            }

            else -> {
                itemName
            }
        }

    companion object {
        /** 혈압. 입력으로 받지 않는다 (400 ITEM_NOT_ALLOWED). */
        val BLOCKED_CODES: Set<String> = setOf("005", "006")

        private val byCode: Map<String, FitnessItem> = entries.associateBy { it.code }

        fun findByCode(code: String): FitnessItem? = byCode[code]

        /** 입력 코드를 카탈로그 항목으로 바꾼다. 005·006 → [ItemNotAllowedException], 그 외 미등록 → [UnknownItemException]. */
        fun resolve(code: String): FitnessItem {
            if (code in BLOCKED_CODES) throw ItemNotAllowedException(code)
            return byCode[code] ?: throw UnknownItemException(code)
        }

        /** 연령대 측정 항목. EASY(필수) 먼저, 그 다음 EQUIPMENT(선택), 각각 코드순. `sex` 는 항목을 바꾸지 않는다. */
        fun forAgeGroup(ageGroup: AgeGroup): List<FitnessItem> =
            entries
                .filter { it.isFor(ageGroup) }
                .sortedWith(compareBy<FitnessItem> { it.inputGroup }.thenBy { it.code })
    }
}
