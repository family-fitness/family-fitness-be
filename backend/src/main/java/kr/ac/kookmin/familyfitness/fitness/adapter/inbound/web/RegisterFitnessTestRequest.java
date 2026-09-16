package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;
import org.jspecify.annotations.Nullable;

// ---- POST /profiles/{profileId}/fitness-tests ----
public record RegisterFitnessTestRequest(
        @NotNull @Nullable LocalDate testedOn,
        @NotNull @Nullable FitnessTestSource source,
        @DecimalMin("30") @DecimalMax("230") @Nullable BigDecimal heightCm,
        @DecimalMin("5") @DecimalMax("250") @Nullable BigDecimal weightKg,
        /** 비어 있으면 도메인이 400 NO_ITEMS 로 거부한다 — 여기서 @NotEmpty 로 막지 않는다. */
        @NotNull @Valid @Nullable List<ItemInput> items) {
    public record ItemInput(
            @NotBlank @Nullable String itemCode,

            @NotNull @Digits(integer = 5, fraction = 3) @Nullable
            BigDecimal value) {}

    public List<Measurement> measurements() {
        return Objects.requireNonNull(items).stream()
                .map(it -> new Measurement(Objects.requireNonNull(it.itemCode()), Objects.requireNonNull(it.value())))
                .toList();
    }
}
