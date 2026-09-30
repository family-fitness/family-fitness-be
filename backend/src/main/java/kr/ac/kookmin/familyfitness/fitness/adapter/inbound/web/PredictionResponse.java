package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction;
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionScenario;
import org.jspecify.annotations.Nullable;

public record PredictionResponse(
        UUID predictionId, String modelVersion, String basis, List<Point> points, String notice) {
    public record Point(
            PredictionScenario scenario,
            String itemCode,
            int yearsFromNow,
            @Nullable BigDecimal p10,
            @Nullable BigDecimal p50,
            @Nullable BigDecimal p90) {}

    public static PredictionResponse of(Prediction prediction) {
        return new PredictionResponse(
                prediction.getId(),
                prediction.getModelVersion(),
                prediction.getBasis(),
                prediction.getPoints().stream()
                        .map(it -> new Point(
                                it.scenario(), it.itemCode(), it.yearsFromNow(), it.p10(), it.p50(), it.p90()))
                        .toList(),
                prediction.getNotice());
    }
}
