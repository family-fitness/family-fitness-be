package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;

public record OwnerRequest(
        @NotBlank @Size(min = 1, max = 20) String name,
        @PastOrPresent LocalDate birthDate,
        Sex sex) {}
