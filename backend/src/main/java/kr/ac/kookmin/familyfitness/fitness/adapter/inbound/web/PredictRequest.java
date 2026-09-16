package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// ---- POST /profiles/{profileId}/predictions ----
public record PredictRequest(
        @Nullable UUID fitnessTestId,
        @Min(1) @Max(10) @Nullable Integer horizonYears,
        @Nullable String itemCode) {}
