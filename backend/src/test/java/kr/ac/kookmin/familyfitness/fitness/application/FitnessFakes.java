package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.domain.PeerQuantiles;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

public final class FitnessFakes {
    private FitnessFakes() {}

    /** 또래 분포 한 칸을 짧게 만든다 — 백분위 i 의 값이 {@code from + step × i} 인 곧은 분포(표본 1,000). */
    public static PeerQuantiles peers(AgeGroup ageGroup, Sex sex, int age, String itemCode, double from, double step) {
        List<Double> quantiles = new ArrayList<>();
        for (int i = 0; i <= 100; i++) quantiles.add(from + step * i);
        return new PeerQuantiles(ageGroup, sex, age, itemCode, 1000, quantiles);
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

    /** 계정이 붙은 보호자(PARENT) 프로필 요약. 호출 계정이 부모인 경우를 흉내 낸다. */
    public static ProfileSummary parentOf(UUID profileId, UUID familyId) {
        return new ProfileSummary(
                profileId,
                familyId,
                "엄마",
                ProfileRole.PARENT,
                AgeGroup.ADULT,
                Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                false,
                true);
    }

    /** 계정이 붙은 자녀(CHILD) 프로필 요약. 호출 계정이 아이 본인 계정인 경우를 흉내 낸다. */
    public static ProfileSummary childAccountOf(UUID profileId, UUID familyId) {
        return new ProfileSummary(
                profileId,
                familyId,
                "첫째",
                ProfileRole.CHILD,
                AgeGroup.ADOLESCENT,
                Sex.M,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                true,
                true);
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
