package kr.ac.kookmin.familyfitness.fitness.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 등급. 와이어 값은 한글 라벨. 백분위→등급: 1등급≥85 · 2등급≥65 · 3등급≥40 · 그 외 참가(BE 설계안 ⑪). */
public enum Grade {
    FIRST("1등급"),
    SECOND("2등급"),
    THIRD("3등급"),
    PARTICIPATION("참가");

    public static final int FIRST_FROM = 85;
    public static final int SECOND_FROM = 65;
    public static final int THIRD_FROM = 40;

    private final String label;

    Grade(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    public static Grade ofPercentile(int percentile) {
        if (percentile >= FIRST_FROM) return FIRST;
        if (percentile >= SECOND_FROM) return SECOND;
        if (percentile >= THIRD_FROM) return THIRD;
        return PARTICIPATION;
    }

    public static Grade fromLabel(String label) {
        return Arrays.stream(values())
                .filter(it -> it.label.equals(label) || it.name().equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 등급: " + label));
    }
}
