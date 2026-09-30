package kr.ac.kookmin.familyfitness.shared.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 백분위 구간. 와이어 값은 소문자(`strength` 등). 화면에 코드값을 그대로 쓰지 않고 {@link #getCopy()} 문구로 바꾼다.
 * 「부족」·「미달」·「하위」는 쓰지 않는다.
 */
public enum Band {
    STRENGTH("strength", "잘하고 있는 영역"),
    STEADY("steady", "꾸준히 하고 있는 영역"),
    GROWTH("growth", "지금 키우기 좋은 영역");

    public static final int STRENGTH_FROM = 75;
    public static final int GROWTH_BELOW = 25;

    private final String wire;
    private final String copy;

    Band(String wire, String copy) {
        this.wire = wire;
        this.copy = copy;
    }

    @JsonValue
    public String getWire() {
        return wire;
    }

    public String getCopy() {
        return copy;
    }

    public static Band ofPercentile(int percentile) {
        if (percentile >= STRENGTH_FROM) return STRENGTH;
        if (percentile < GROWTH_BELOW) return GROWTH;
        return STEADY;
    }
}
