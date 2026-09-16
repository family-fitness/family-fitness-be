package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 참여 수준(PARENT 본인 프로필)·보호자 동의(가족의 PARENT) 변경. */
@Service
@Transactional
public class ProfileSettingsService {
    private final FamilyRepository families;
    private final ProfileSummaries summaries;
    private final IdentityClock clock;

    public ProfileSettingsService(FamilyRepository families, ProfileSummaries summaries, IdentityClock clock) {
        this.families = families;
        this.summaries = summaries;
        this.clock = clock;
    }

    public ProfileSummary changeSupportMode(UUID userId, UUID profileId, SupportMode mode) {
        Family family = load(profileId);
        Profile profile = family.changeSupportMode(userId, profileId, mode);
        families.save(family);
        return summaries.summary(profile);
    }

    public ConsentState updateConsent(UUID userId, UUID profileId, GuardianConsent decision) {
        Family family = load(profileId);
        Profile profile = family.updateConsent(userId, profileId, decision, clock.now());
        families.save(family);
        boolean given = profile.getConsent().isGiven();
        return new ConsentState(
                profile.consentGiven(clock.today()),
                given ? profile.getConsent().personalAt() : null,
                given ? profile.getConsent().byUserId() : null,
                profile.measurable(clock.today()));
    }

    private Family load(UUID profileId) {
        Family family = families.findByProfileId(profileId);
        if (family == null) throw new ProfileNotFoundException(profileId);
        return family;
    }
}
