package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

public record AddMemberRequest(
        @NotBlank @Size(min = 1, max = 20) String name,
        @NotNull @PastOrPresent @Nullable LocalDate birthDate,
        @NotNull @Nullable Sex sex,
        @NotNull @Nullable ProfileRole role,
        /** 가입 때 적은 키. 범위는 측정 등록과 같다. 안 적었으면 null 이나 칸을 뺀다(0 은 범위 밖이라 400). */
        @DecimalMin("30") @DecimalMax("230") @Nullable BigDecimal heightCm,
        /** 가입 때 적은 몸무게. 범위는 측정 등록과 같다. 응답에는 싣지 않는다. */
        @DecimalMin("5") @DecimalMax("250") @Nullable BigDecimal weightKg,
        @Valid @Nullable GuardianConsentRequest guardianConsent) {}
