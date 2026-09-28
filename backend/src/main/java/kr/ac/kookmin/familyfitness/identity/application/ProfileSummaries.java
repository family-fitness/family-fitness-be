package kr.ac.kookmin.familyfitness.identity.application;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import org.springframework.stereotype.Component;

/** 도메인 {@link Profile} → 공개 언어({@link ProfileSummary}·{@link ProfileDetails}). 나이·만료 판정은 현재 시각 기준. */
@Component
public class ProfileSummaries {
    private final IdentityClock clock;

    public ProfileSummaries(IdentityClock clock) {
        this.clock = clock;
    }

    public ProfileSummary summary(Profile profile) {
        LocalDate today = clock.today();
        return new ProfileSummary(
                profile.getId(),
                profile.getFamilyId(),
                profile.getDisplayName(),
                profile.getRole(),
                profile.ageGroup(today),
                profile.getSex(),
                profile.hasAccount(),
                profile.inviteStatus(clock.now()),
                profile.getSupportMode(),
                profile.measurable(today),
                profile.consentRequired(today),
                profile.consentGiven(today));
    }

    public ProfileDetails details(Profile profile) {
        return new ProfileDetails(
                profile.getId(),
                profile.getFamilyId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getRole(),
                profile.getBirthDate(),
                profile.getSex(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getSupportMode(),
                profile.consentGiven(clock.today()));
    }
}
