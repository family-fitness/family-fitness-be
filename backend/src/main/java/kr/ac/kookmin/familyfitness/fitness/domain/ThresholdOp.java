package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * 등급 기준표 한 줄의 비교. 와이어 값은 기준표(`fitness_grade_thresholds.op`, AI `grade_thresholds.csv`)의 기호 그대로다.
 * 뜻은 AI `stats/tables.py` 의 `Threshold.passes()` 와 같다 — `>=` · `<=` 는 경계를 넣고 `<` 는 뺀다. `between` 은 양 끝을 넣는다.
 */
public enum ThresholdOp {
    AT_LEAST(">="),
    AT_MOST("<="),
    BELOW("<"),
    BETWEEN("between");

    private final String wire;

    ThresholdOp(String wire) {
        this.wire = wire;
    }

    public String getWire() {
        return wire;
    }

    /** {@code upper} 는 {@link #BETWEEN} 의 윗값이다. 다른 비교에서는 보지 않는다. */
    public boolean passes(BigDecimal value, BigDecimal cutoff, @Nullable BigDecimal upper) {
        return switch (this) {
            case AT_LEAST -> value.compareTo(cutoff) >= 0;
            case AT_MOST -> value.compareTo(cutoff) <= 0;
            case BELOW -> value.compareTo(cutoff) < 0;
            case BETWEEN -> upper != null && value.compareTo(cutoff) >= 0 && value.compareTo(upper) <= 0;
        };
    }

    public static ThresholdOp of(String wire) {
        return Arrays.stream(values())
                .filter(it -> it.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 기준 비교: " + wire));
    }
}
