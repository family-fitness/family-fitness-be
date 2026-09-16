package kr.ac.kookmin.familyfitness.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;

/** 체력 요인. 와이어 값은 한글 라벨. 레이더 차트 5요인은 {@link #RADAR} 순서를 따른다. */
public enum FitnessFactor {
    CARDIO("심폐지구력"),
    STRENGTH("근력"),
    MUSCULAR_ENDURANCE("근지구력"),
    FLEXIBILITY("유연성"),
    AGILITY("민첩성"),
    POWER("순발력"),
    COORDINATION("협응력"),
    BALANCE("평형성");

    /** 결과 화면 레이더 차트 — 근력 · 근지구력 · 유연성 · 심폐지구력 · 순발력 */
    public static final List<FitnessFactor> RADAR = List.of(STRENGTH, MUSCULAR_ENDURANCE, FLEXIBILITY, CARDIO, POWER);

    private final String label;

    FitnessFactor(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static FitnessFactor fromLabel(String label) {
        return Arrays.stream(values())
                .filter(it -> it.label.equals(label) || it.name().equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 체력 요인: " + label));
    }
}
