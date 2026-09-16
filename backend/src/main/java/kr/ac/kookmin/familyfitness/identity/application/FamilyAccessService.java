package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가족 단위 권한 판단({@link FamilyAccess}) 구현. HTTP 요청의 role·familyId 를 믿지 않는다. */
@Service
@Transactional(readOnly = true)
public class FamilyAccessService implements FamilyAccess {
    private final FamilyRepository families;
    private final ProfileSummaries summaries;

    public FamilyAccessService(FamilyRepository families, ProfileSummaries summaries) {
        this.families = families;
        this.summaries = summaries;
    }

    @Override
    public ProfileSummary requireMember(UUID userId, UUID familyId) {
        return memberOf(family(familyId), userId);
    }

    @Override
    public ProfileSummary requireParent(UUID userId, UUID familyId) {
        return parentOf(family(familyId), userId);
    }

    @Override
    public @Nullable ProfileSummary memberOf(UUID userId, UUID familyId) {
        Family family = families.findById(familyId);
        if (family == null) return null;
        Profile member = family.memberOf(userId);
        return member == null ? null : summaries.summary(member);
    }

    @Override
    public ProfileSummary requireSameFamilyAsProfile(UUID userId, UUID profileId) {
        Family family = familyOfProfile(profileId);
        memberOf(family, userId);
        return summaries.summary(family.profile(profileId));
    }

    @Override
    public ProfileSummary requireParentOfProfile(UUID userId, UUID profileId) {
        return parentOf(familyOfProfile(profileId), userId);
    }

    private Family family(UUID familyId) {
        Family family = families.findById(familyId);
        if (family == null) throw new FamilyNotFoundException(familyId);
        return family;
    }

    private Family familyOfProfile(UUID profileId) {
        Family family = families.findByProfileId(profileId);
        if (family == null) throw new ProfileNotFoundException(profileId);
        return family;
    }

    private ProfileSummary memberOf(Family family, UUID userId) {
        Profile member = family.memberOf(userId);
        if (member == null) throw new NotSameFamilyException();
        return summaries.summary(member);
    }

    private ProfileSummary parentOf(Family family, UUID userId) {
        ProfileSummary member = memberOf(family, userId);
        if (!member.isParent()) throw new NotAParentException();
        return member;
    }
}
