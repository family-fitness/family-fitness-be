package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

public record AddMemberRequest(
        @NotBlank @Size(min = 1, max = 20) String name,
        @PastOrPresent LocalDate birthDate,
        Sex sex,
        ProfileRole role,
        @Valid @Nullable GuardianConsentRequest guardianConsent) {}
