package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.math.BigDecimal;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.Grade;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import org.jspecify.annotations.Nullable;

public record ItemResult(
        String itemCode,
        String itemLabel,
        String unit,
        BigDecimal value,
        @Nullable Integer percentile,
        @Nullable Grade grade,
        @Nullable Band band,
        @Nullable String topPercentText) {
    static List<ItemResult> of(FitnessTest test) {
        return test.getItems().stream()
                .map(it -> new ItemResult(
                        it.item().getCode(),
                        it.item().label(test.getAgeGroup()),
                        it.item().getUnit(),
                        it.value(),
                        it.score().percentile(),
                        it.score().grade(),
                        it.score().band(),
                        it.score().topPercentText()))
                .toList();
    }
}
