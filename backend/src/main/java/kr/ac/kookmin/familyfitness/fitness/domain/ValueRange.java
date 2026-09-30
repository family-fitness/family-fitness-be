package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;

/**
 * 항목 값이 들어올 수 있는 범위(양 끝 포함, 잠정 값). `GET /fitness/items` 가 이 값을 알려 주고,
 * 등록은 밖의 값을 400 {@code ITEM_OUT_OF_RANGE} 로 거부한다. 프론트 측정 폼도 같은 조건(min ≤ 값 ≤ max)으로 막는다.
 */
public record ValueRange(int min, int max) {
    public boolean contains(BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(min)) >= 0 && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }
}
