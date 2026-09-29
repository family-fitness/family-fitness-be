package kr.ac.kookmin.familyfitness.fitness.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * 국민체력100 인증 등급. 와이어 값은 한글 라벨이고 1 · 2 · 3등급과 참가 넷뿐이다(FE `Grade` 타입). 한 사람에게 하나를
 * 공식 기준표로 매긴다({@link Certifier}) — 백분위에서 셈하지 않고, 항목마다 매기지도 않는다.
 */
public enum Grade {
    FIRST("1등급"),
    SECOND("2등급"),
    THIRD("3등급"),
    PARTICIPATION("참가");

    private final String label;

    Grade(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    public static Grade fromLabel(String label) {
        return Arrays.stream(values())
                .filter(it -> it.label.equals(label) || it.name().equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 등급: " + label));
    }
}
