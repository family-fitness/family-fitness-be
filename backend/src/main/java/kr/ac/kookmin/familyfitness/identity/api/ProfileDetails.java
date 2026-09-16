package kr.ac.kookmin.familyfitness.identity.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/**
 * 측정·편성 계산에 필요한 내부용 상세. AI 서비스로는 이 값을 그대로 보내지 않고
 * 나이·성별·신장·체중만 {@link kr.ac.kookmin.familyfitness.shared.domain.ProfileRef} 와 함께 보낸다.
 */
public record ProfileDetails(
        UUID profileId,
        UUID familyId,
        @Nullable UUID userId,
        String name,
        ProfileRole role,
        LocalDate birthDate,
        Sex sex,
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg,
        @Nullable SupportMode supportMode,
        boolean consentGiven) {}
