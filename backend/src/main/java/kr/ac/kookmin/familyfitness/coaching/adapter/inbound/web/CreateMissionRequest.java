package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import org.jspecify.annotations.Nullable;

public record CreateMissionRequest(
        @NotBlank @Size(min = 1, max = 50) String title,
        @NotNull @Nullable LocalDate startDate,
        @NotNull @Nullable LocalDate endDate,
        @NotNull @Nullable TargetMetric targetMetric,
        @NotNull @Min(1) @Nullable Integer targetValue,
        @Nullable String videoId,
        @NotEmpty @Size(min = 1, max = 5) List<UUID> participantProfileIds) {}
