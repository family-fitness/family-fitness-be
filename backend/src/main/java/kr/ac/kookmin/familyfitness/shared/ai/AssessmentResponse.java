package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AssessmentResponse(
        String inputLevel,
        String ageGroup,
        ChildScope childScope,
        ParentScope parentScope,
        boolean lowSample,
        String disclaimer) {
    public record ChildScope(@Nullable FocusOne focusOne) {}

    public record FocusOne(String factor, String copy) {}

    public record ParentScope(
            @Nullable String grade,
            List<GradeRatio> peerDistribution,
            List<FactorScore> factors,
            Map<String, String> copy) {}

    public record GradeRatio(String grade, double ratio) {}

    public record FactorScore(
            String factor,
            String itemCode,
            String itemName,
            String itemLabel,
            String unit,
            @Nullable Double value,
            @Nullable Double score,
            @Nullable Integer percentile,
            @Nullable String band,
            int n) {}
}
