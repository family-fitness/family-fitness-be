package kr.ac.kookmin.familyfitness.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * 국민체력100 연령대. 와이어 값은 한글 라벨(`유아기` 등)이며 생년월일에서 파생한다.
 * 만 4세 미만도 {@link #TODDLER} 로 분류하되 규준이 없어 측정 대상이 아니다 ({@link Ages#isMeasurable}).
 */
public enum AgeGroup {
    TODDLER("유아기"),
    YOUTH("유소년"),
    ADOLESCENT("청소년"),
    ADULT("성인"),
    SENIOR("어르신");

    private final String label;

    AgeGroup(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /** AI 서비스 `age_unit`. 유아기만 개월 단위를 쓴다. */
    public String getAgeUnit() {
        return this == TODDLER ? "개월" : "세";
    }

    public static AgeGroup ofAge(int fullYears) {
        if (fullYears < 7) return TODDLER;
        if (fullYears < 13) return YOUTH;
        if (fullYears < 19) return ADOLESCENT;
        if (fullYears < 65) return ADULT;
        return SENIOR;
    }

    public static AgeGroup of(LocalDate birthDate, LocalDate on) {
        return ofAge(Ages.fullYears(birthDate, on));
    }

    @JsonCreator
    public static AgeGroup fromLabel(String label) {
        return Arrays.stream(values())
                .filter(it -> it.label.equals(label) || it.name().equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 연령대: " + label));
    }
}
