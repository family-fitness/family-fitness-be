package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 프로필을 읽는 통로({@link ProfileQuery}) 구현. */
@Service
@Transactional(readOnly = true)
public class ProfileQueryService implements ProfileQuery {
    private final FamilyRepository families;
    private final ProfileSummaries summaries;

    public ProfileQueryService(FamilyRepository families, ProfileSummaries summaries) {
        this.families = families;
        this.summaries = summaries;
    }

    @Override
    public @Nullable ProfileSummary findSummary(UUID profileId) {
        Profile profile = profileOf(profileId);
        return profile == null ? null : summaries.summary(profile);
    }

    @Override
    public @Nullable ProfileDetails findDetails(UUID profileId) {
        Profile profile = profileOf(profileId);
        return profile == null ? null : summaries.details(profile);
    }

    @Override
    public List<ProfileSummary> summariesOfFamily(UUID familyId) {
        Family family = families.findById(familyId);
        return family == null
                ? List.of()
                : family.getProfiles().stream().map(summaries::summary).toList();
    }

    @Override
    public List<ProfileDetails> detailsOfFamily(UUID familyId) {
        Family family = families.findById(familyId);
        return family == null
                ? List.of()
                : family.getProfiles().stream().map(summaries::details).toList();
    }

    @Override
    public List<ProfileSummary> summariesOfUser(UUID userId) {
        return families.profilesOfUser(userId).stream().map(summaries::summary).toList();
    }

    @Override
    public @Nullable String familyName(UUID familyId) {
        Family family = families.findById(familyId);
        return family == null ? null : family.getName();
    }

    @Override
    public List<UUID> allFamilyIds() {
        return families.allIds();
    }

    private @Nullable Profile profileOf(UUID profileId) {
        Family family = families.findByProfileId(profileId);
        return family == null ? null : family.profileOrNull(profileId);
    }
}
