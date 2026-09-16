package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;

public final class Summaries {
    private Summaries() {}

    public static ProfileSummary summary(ProfileDetails details) {
        return summary(details, Fixed.TODAY);
    }

    public static ProfileSummary summary(ProfileDetails details, LocalDate on) {
        return new ProfileSummary(
                details.profileId(),
                details.familyId(),
                details.name(),
                details.role(),
                AgeGroup.of(details.birthDate(), on),
                details.userId() != null,
                details.userId() != null ? InviteStatus.CLAIMED : InviteStatus.NONE,
                details.supportMode(),
                true,
                false,
                details.consentGiven());
    }
}
