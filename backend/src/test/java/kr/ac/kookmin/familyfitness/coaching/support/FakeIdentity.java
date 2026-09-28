package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/** identity 공개 API 의 결정적 가짜. 여러 가족을 등록할 수 있다. */
public class FakeIdentity implements ProfileQuery, FamilyAccess, CheerQuery {
    public final List<Family> families;
    public int cheerCount = 0;

    public FakeIdentity(Family... families) {
        this.families = new ArrayList<>(List.of(families));
    }

    private List<ProfileDetails> all() {
        return families.stream().flatMap(it -> it.members().stream()).toList();
    }

    @Override
    public @Nullable ProfileSummary findSummary(UUID profileId) {
        ProfileDetails details = findDetails(profileId);
        return details == null ? null : Summaries.summary(details);
    }

    @Override
    public @Nullable ProfileDetails findDetails(UUID profileId) {
        return all().stream()
                .filter(it -> it.profileId().equals(profileId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<ProfileSummary> summariesOfFamily(UUID familyId) {
        return detailsOfFamily(familyId).stream().map(Summaries::summary).toList();
    }

    @Override
    public List<ProfileDetails> detailsOfFamily(UUID familyId) {
        return all().stream().filter(it -> it.familyId().equals(familyId)).toList();
    }

    @Override
    public List<ProfileSummary> summariesOfUser(UUID userId) {
        return all().stream()
                .filter(it -> userId.equals(it.userId()))
                .map(Summaries::summary)
                .toList();
    }

    @Override
    public @Nullable String familyName(UUID familyId) {
        return families.stream().anyMatch(it -> it.familyId.equals(familyId)) ? "가족" : null;
    }

    @Override
    public List<UUID> allFamilyIds() {
        return families.stream().map(it -> it.familyId).toList();
    }

    @Override
    public ProfileSummary requireMember(UUID userId, UUID familyId) {
        ProfileSummary member = memberOf(userId, familyId);
        if (member == null) throw new NotSameFamilyException();
        return member;
    }

    @Override
    public ProfileSummary requireParent(UUID userId, UUID familyId) {
        ProfileSummary member = requireMember(userId, familyId);
        if (!member.isParent()) throw new NotAParentException();
        return member;
    }

    @Override
    public @Nullable ProfileSummary memberOf(UUID userId, UUID familyId) {
        return all().stream()
                .filter(it -> userId.equals(it.userId()) && it.familyId().equals(familyId))
                .findFirst()
                .map(Summaries::summary)
                .orElse(null);
    }

    @Override
    public ProfileSummary requireSameFamilyAsProfile(UUID userId, UUID profileId) {
        ProfileDetails target = findDetails(profileId);
        if (target == null) throw new ProfileNotFoundException(profileId);
        requireMember(userId, target.familyId());
        return Summaries.summary(target);
    }

    @Override
    public ProfileSummary requireParentOfProfile(UUID userId, UUID profileId) {
        ProfileDetails target = findDetails(profileId);
        if (target == null) throw new ProfileNotFoundException(profileId);
        return requireParent(userId, target.familyId());
    }

    @Override
    public ProfileSummary requireActingAs(UUID userId, UUID profileId) {
        ProfileDetails target = findDetails(profileId);
        if (target == null) throw new ProfileNotFoundException(profileId);
        ProfileSummary actor = requireMember(userId, target.familyId());
        boolean own = userId.equals(target.userId());
        boolean accountlessChild = actor.isParent() && target.role() == ProfileRole.CHILD && target.userId() == null;
        if (!own && !accountlessChild) throw new CannotActAsProfileException();
        return Summaries.summary(target);
    }

    @Override
    public int countCheers(UUID familyId, Instant from, Instant to) {
        return cheerCount;
    }

    /** coaching 시험은 받은 응원을 읽지 않는다. */
    @Override
    public List<CheerView> received(UUID toProfileId, Instant from, Instant to) {
        return List.of();
    }
}
