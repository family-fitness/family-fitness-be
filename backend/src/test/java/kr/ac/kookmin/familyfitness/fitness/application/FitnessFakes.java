package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

public final class FitnessFakes {
    private FitnessFakes() {}

    public record NormPair(int percentile, double value) {}

    /** 규준 한 구간을 짧게 만든다. */
    public static List<NormPoint> norms(String itemCode, Sex sex, int ageFrom, int ageTo, NormPair... pairs) {
        List<NormPoint> points = new ArrayList<>();
        for (NormPair pair : pairs) {
            points.add(new NormPoint(itemCode, sex, ageFrom, ageTo, pair.percentile(), pair.value(), 1900));
        }
        return List.copyOf(points);
    }

    public static ProfileSummary summaryOf(UUID profileId, UUID familyId, AgeGroup ageGroup) {
        return summaryOf(profileId, familyId, ageGroup, true, true, true);
    }

    public static ProfileSummary summaryOf(
            UUID profileId,
            UUID familyId,
            AgeGroup ageGroup,
            boolean measurable,
            boolean consentRequired,
            boolean consentGiven) {
        return new ProfileSummary(
                profileId,
                familyId,
                "아이",
                ProfileRole.CHILD,
                ageGroup,
                Sex.F,
                false,
                InviteStatus.NONE,
                null,
                measurable,
                consentRequired,
                consentGiven);
    }

    public static ProfileDetails detailsOf(UUID profileId, UUID familyId, LocalDate birthDate) {
        return detailsOf(profileId, familyId, birthDate, Sex.F, null, null, true);
    }

    public static ProfileDetails detailsOf(
            UUID profileId,
            UUID familyId,
            LocalDate birthDate,
            Sex sex,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            boolean consentGiven) {
        return new ProfileDetails(
                profileId,
                familyId,
                null,
                "아이",
                ProfileRole.CHILD,
                birthDate,
                sex,
                heightCm,
                weightKg,
                null,
                consentGiven);
    }
}
