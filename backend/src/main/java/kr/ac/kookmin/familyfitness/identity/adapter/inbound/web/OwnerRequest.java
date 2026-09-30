package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

public record OwnerRequest(
        @NotBlank @Size(min = 1, max = 20) String name,
        @NotNull @PastOrPresent @Nullable LocalDate birthDate,
        @NotNull @Nullable Sex sex) {}
