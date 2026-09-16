package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record TrajectoryResponse(
        String basis,
        String itemCode,
        String itemName,
        String unit,
        List<Band> bands,
        String notice,
        boolean lowSample) {
    public record Band(
            int age,
            @Nullable Double p10,
            @Nullable Double p50,
            @Nullable Double p90,
            int n) {}
}
