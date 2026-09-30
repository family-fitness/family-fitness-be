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

    /** 자녀 계정용 — 잰 값만 싣고 백분위 · 등급 · 구간 · 「상위 n%」 는 비운다. */
    static List<ItemResult> valuesOnly(FitnessTest test) {
        return test.getItems().stream()
                .map(it -> new ItemResult(
                        it.item().getCode(),
                        it.item().label(test.getAgeGroup()),
                        it.item().getUnit(),
                        it.value(),
                        null,
                        null,
                        null,
                        null))
                .toList();
    }
}
