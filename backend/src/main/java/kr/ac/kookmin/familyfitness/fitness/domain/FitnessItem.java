package kr.ac.kookmin.familyfitness.fitness.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 측정 항목 카탈로그 (계약 §0 · AI 명세 §9 · `family-fitness-ai/stats/items.py`).
 * 코드가 식별자, 이름은 표기. 신체조성(003·004·018·042)과 혈압(005·006)은 카탈로그에 없다.
 */
public enum FitnessItem {
    SIT_UP(
            "009",
            "윗몸말아올리기",
            "회",
            FitnessFactor.MUSCULAR_ENDURANCE,
            true,
            InputGroup.EASY,
            null,
            new ValueRange(0, 120),
            EnumSet.of(AgeGroup.TODDLER, AgeGroup.YOUTH, AgeGroup.ADOLESCENT)),
    REPEATED_JUMP(
            "010",
            "반복점프",
            "회",
            FitnessFactor.MUSCULAR_ENDURANCE,
            true,
            InputGroup.EASY,
            null,
            new ValueRange(0, 120),
            EnumSet.of(AgeGroup.ADOLESCENT)),
    SIT_AND_REACH(
            "012",
            "앉아윗몸앞으로굽히기",
            "cm",
            FitnessFactor.FLEXIBILITY,
            true,
            InputGroup.EASY,
            null,
            new ValueRange(-30, 40),
            EnumSet.allOf(AgeGroup.class)),
    ILLINOIS(
            "013",
            "일리노이",
            "초",
            FitnessFactor.AGILITY,
            false,
            InputGroup.EQUIPMENT,
            Equipment.SPACE,
            new ValueRange(5, 60),
            EnumSet.of(AgeGroup.ADOLESCENT)),
    FLIGHT_TIME(
            "014",
            "체공시간",
            "초",
            FitnessFactor.POWER,
            true,
            InputGroup.EASY,
            null,
            new ValueRange(0, 2),
            EnumSet.of(AgeGroup.ADOLESCENT)),
    EYE_HAND_COORDINATION(
            "017",
            "눈-손협응력",
            "초",
            FitnessFactor.COORDINATION,
            false,
            InputGroup.EQUIPMENT,
            Equipment.DEVICE,
            new ValueRange(0, 120),
            EnumSet.of(AgeGroup.ADOLESCENT)),
    CROSS_SIT_UP(
            "019",
            "교차윗몸일으키기",
            "회",
            FitnessFactor.MUSCULAR_ENDURANCE,
            true,
            InputGroup.EASY,
            null,
            new ValueRange(0, 120),
            EnumSet.of(AgeGroup.ADULT, AgeGroup.SENIOR)),
    SHUTTLE_RUN(
            "020",
            "왕복오래달리기",
            "회",
            FitnessFactor.CARDIO,
            true,
            InputGroup.EQUIPMENT,
            Equipment.SPACE,
            new ValueRange(0, 150),
            EnumSet.of(AgeGroup.TODDLER, AgeGroup.YOUTH, AgeGroup.ADOLESCENT, AgeGroup.ADULT)),
    SHUTTLE_RUN_10M_X4(
            "021",
            "10m4회왕복달리기",
            "초",
            FitnessFactor.AGILITY,
            false,
            InputGroup.EQUIPMENT,
            Equipment.SPACE,
            new ValueRange(5, 60),
            EnumSet.of(AgeGroup.ADULT)),
    STANDING_LONG_JUMP(
            "022",
            "제자리멀리뛰기",
            "cm",
            FitnessFactor.POWER,
            true,
            InputGroup.EQUIPMENT,
            Equipment.SPACE,
            new ValueRange(0, 350),
            EnumSet.of(AgeGroup.TODDLER, AgeGroup.YOUTH, AgeGroup.ADULT)),
    RELATIVE_GRIP(
            "028",
            "상대악력",
            "%",
            FitnessFactor.STRENGTH,
            true,
            InputGroup.EQUIPMENT,
            "악력계",
            new ValueRange(0, 150),
            EnumSet.allOf(AgeGroup.class)),
    TREADMILL_VO2MAX(
            "035",
            "트레드밀VO2max",
            "ml/kg/min",
            FitnessFactor.CARDIO,
            true,
            InputGroup.EQUIPMENT,
            Equipment.DEVICE,
            new ValueRange(10, 90),
            EnumSet.of(AgeGroup.ADOLESCENT, AgeGroup.ADULT)),
    STEP_VO2MAX(
            "037",
            "스텝검사VO2max",
            "ml/kg/min",
            FitnessFactor.CARDIO,
            true,
            InputGroup.EQUIPMENT,
            Equipment.DEVICE,
            new ValueRange(10, 90),
            EnumSet.of(AgeGroup.ADOLESCENT, AgeGroup.ADULT)),
    REACTION_TIME(
            "040",
            "반응시간",
            "초",
            FitnessFactor.AGILITY,
            false,
            InputGroup.EQUIPMENT,
            Equipment.DEVICE,
            new ValueRange(0, 5),
            EnumSet.of(AgeGroup.ADULT)),
    ADULT_FLIGHT_TIME(
            "041",
            "성인체공시간",
            "초",
            FitnessFactor.POWER,
            true,
            InputGroup.EASY,
            null,
            new ValueRange(0, 2),
            EnumSet.of(AgeGroup.ADULT)),
    SIDE_STEP(
            "043",
            "반복옆뛰기",
            "회",
            FitnessFactor.AGILITY,
            true,
            InputGroup.EASY,
            null,
            new ValueRange(0, 120),
            EnumSet.of(AgeGroup.YOUTH)),
    /**
     * 유소년의 협응력 시험 — 벽에 공을 던지고 받은 횟수(AI `common/items.py` 044, 017 과는 다른 시험). 벽과 공이 있어야 해서 선택 항목이다.
     * 범위 0~60: 공식 1등급 기준이 18~19회, 공공데이터 11~12세 99번째 백분위가 12~17회, 최댓값이 33~50회(여 12세 90회 한 건은
     * 튀는 값)라 기준의 세 배쯤 넉넉히 둔다.
     */
    WALL_PASS(
            "044",
            "눈-손협응력(벽패스)",
            "회",
            FitnessFactor.COORDINATION,
            true,
            InputGroup.EQUIPMENT,
            Equipment.WALL_AND_BALL,
            new ValueRange(0, 60),
            EnumSet.of(AgeGroup.YOUTH)),
    SHUTTLE_RUN_5M_X4(
            "050",
            "5m4회왕복달리기",
            "초",
            FitnessFactor.AGILITY,
            false,
            InputGroup.EQUIPMENT,
            Equipment.SPACE,
            new ValueRange(5, 60),
            EnumSet.of(AgeGroup.TODDLER)),
    BUTTON_PRESS_3X3(
            "051",
            "3x3버튼누르기",
            "초",
            FitnessFactor.COORDINATION,
            false,
            InputGroup.EQUIPMENT,
            Equipment.DEVICE,
            new ValueRange(0, 60),
            EnumSet.of(AgeGroup.TODDLER));

    private static final class Equipment {
        private static final String SPACE = "공간";
        private static final String DEVICE = "장비";
        private static final String WALL_AND_BALL = "벽·공";
    }

    /** 혈압. 입력으로 받지 않는다 (400 ITEM_NOT_ALLOWED). */
    public static final Set<String> BLOCKED_CODES = Set.of("005", "006");

    private final String code;
    private final String itemName;
    private final String unit;
    private final FitnessFactor factor;
    private final boolean higherIsBetter;
    private final InputGroup inputGroup;

    /** 필요한 장비·공간. EASY 항목은 null. ▲ 확정 필요 — 세부 장비명은 계약에 없어 「공간」·「장비」로만 적는다. */
    private final @Nullable String equipment;

    private final ValueRange range;
    private final Set<AgeGroup> ageGroups;

    FitnessItem(
            String code,
            String itemName,
            String unit,
            FitnessFactor factor,
            boolean higherIsBetter,
            InputGroup inputGroup,
            @Nullable String equipment,
            ValueRange range,
            Set<AgeGroup> ageGroups) {
        this.code = code;
        this.itemName = itemName;
        this.unit = unit;
        this.factor = factor;
        this.higherIsBetter = higherIsBetter;
        this.inputGroup = inputGroup;
        this.equipment = equipment;
        this.range = range;
        this.ageGroups = ageGroups;
    }

    public String getCode() {
        return code;
    }

    public String getItemName() {
        return itemName;
    }

    public String getUnit() {
        return unit;
    }

    public FitnessFactor getFactor() {
        return factor;
    }

    public boolean isHigherIsBetter() {
        return higherIsBetter;
    }

    public InputGroup getInputGroup() {
        return inputGroup;
    }

    public @Nullable String getEquipment() {
        return equipment;
    }

    public ValueRange getRange() {
        return range;
    }

    /** EQUIPMENT 항목은 선택 입력이다. */
    public boolean isOptional() {
        return inputGroup == InputGroup.EQUIPMENT;
    }

    public boolean isFor(AgeGroup ageGroup) {
        return ageGroups.contains(ageGroup);
    }

    /** 연령대별 표기. 020 은 왕복 거리가 연령대마다 달라 라벨이 갈린다. */
    public String label(AgeGroup ageGroup) {
        if (this != SHUTTLE_RUN) return itemName;
        return switch (ageGroup) {
            case TODDLER -> "10m 왕복오래달리기";
            case YOUTH -> "15m 왕복오래달리기";
            default -> "20m 왕복오래달리기";
        };
    }

    public static @Nullable FitnessItem findByCode(String code) {
        return Holder.BY_CODE.get(code);
    }

    /** 입력 코드를 카탈로그 항목으로 바꾼다. 005·006 → {@link ItemNotAllowedException}, 그 외 미등록 → {@link UnknownItemException}. */
    public static FitnessItem resolve(String code) {
        if (BLOCKED_CODES.contains(code)) throw new ItemNotAllowedException(code);
        FitnessItem item = Holder.BY_CODE.get(code);
        if (item == null) throw new UnknownItemException(code);
        return item;
    }

    /** 연령대 측정 항목. EASY(필수) 먼저, 그 다음 EQUIPMENT(선택), 각각 코드순. `sex` 는 항목을 바꾸지 않는다. */
    public static List<FitnessItem> forAgeGroup(AgeGroup ageGroup) {
        return Arrays.stream(values())
                .filter(it -> it.isFor(ageGroup))
                .sorted(Comparator.comparing(FitnessItem::getInputGroup).thenComparing(FitnessItem::getCode))
                .toList();
    }

    private static final class Holder {
        private static final Map<String, FitnessItem> BY_CODE =
                Arrays.stream(values()).collect(Collectors.toMap(FitnessItem::getCode, Function.identity()));
    }
}
