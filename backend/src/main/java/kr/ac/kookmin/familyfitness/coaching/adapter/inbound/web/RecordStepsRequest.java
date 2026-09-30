package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RecordStepsRequest(
        @NotNull @Nullable UUID profileId,
        @NotNull @Nullable LocalDate activityDate,
        @NotNull @Min(0) @Max(100_000) @Nullable Integer steps) {}
