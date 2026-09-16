package kr.ac.kookmin.familyfitness.coaching.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Ages;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import org.jspecify.annotations.Nullable;

/** AI 로 보낼 프로필. 이름·생년월일·계정 식별자는 싣지 않고 {@link ProfileRef} 로만 가리킨다. */
public final class AiProfileFactory {
    private static final Set<String> NOT_ALLOWED_ITEMS = Set.of("005", "006");

    private AiProfileFactory() {}

    public static AiProfile of(
            ProfileDetails details,
            Map<String, BigDecimal> measurements,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            LocalDate on) {
        AgeGroup ageGroup = AgeGroup.of(details.birthDate(), on);
        BigDecimal height = heightCm != null ? heightCm : details.heightCm();
        BigDecimal weight = weightKg != null ? weightKg : details.weightKg();
        Map<String, Double> values = new LinkedHashMap<>();
        measurements.forEach((code, value) -> {
            if (!NOT_ALLOWED_ITEMS.contains(code)) values.put(code, value.doubleValue());
        });
        return new AiProfile(
                ProfileRef.of(details.profileId()),
                ageGroup == AgeGroup.TODDLER
                        ? Ages.fullMonths(details.birthDate(), on)
                        : Ages.fullYears(details.birthDate(), on),
                ageGroup.getAgeUnit(),
                details.sex().name(),
                height == null ? null : height.doubleValue(),
                weight == null ? null : weight.doubleValue(),
                values);
    }

    public static CoachRunRequest.Participant participant(ProfileDetails details, AiProfile profile) {
        return new CoachRunRequest.Participant(profile, CoachRoles.of(details.role(), details.supportMode()));
    }
}
