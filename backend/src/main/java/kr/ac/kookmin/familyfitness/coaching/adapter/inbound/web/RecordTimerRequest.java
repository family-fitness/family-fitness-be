package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RecordTimerRequest(
        @NotNull @Nullable UUID profileId,
        @NotNull @Nullable Instant startedAt,
        @NotNull @Nullable Instant endedAt,
        @NotNull @Min(1) @Max(180) @Nullable Integer activeMinutes) {}
