package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Ages;

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
                // identity 와 같은 규칙: 만 4세 이상이고 (동의 불필요이거나) 동의가 살아 있다
                Ages.isMeasurable(details.birthDate(), on) && details.consentGiven(),
                Ages.requiresGuardianConsent(details.birthDate(), on),
                details.consentGiven());
    }
}
