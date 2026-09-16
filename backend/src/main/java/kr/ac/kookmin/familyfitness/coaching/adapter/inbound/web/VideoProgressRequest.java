package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record VideoProgressRequest(
        @NotNull @Nullable UUID profileId,

        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") @Nullable
        Double progress,

        @NotNull @Min(0) @Nullable Integer watchedSec,
        @Nullable UUID missionId) {}
