package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** 저장된 항목 한 줄. 백분위와 등급(공식 기준표)은 저장 시점 값으로 굳고, 구간 · 「상위 n%」 는 그 백분위에서 파생된다. */
public record FitnessTestItem(FitnessItem item, BigDecimal value, ItemScore score) {
    public @Nullable Integer percentile() {
        return score.percentile();
    }
}
