package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotNull;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import org.jspecify.annotations.Nullable;

public record GuardianConsentRequest(
        @NotNull @Nullable Boolean personalData,
        @NotNull @Nullable Boolean healthData) {
    public GuardianConsent toDomain() {
        return new GuardianConsent(Boolean.TRUE.equals(personalData), Boolean.TRUE.equals(healthData));
    }
}
