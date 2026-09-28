package kr.ac.kookmin.familyfitness.identity.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyInFamilyException;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가족 생성·구성원 추가·구성원 목록. 권한은 저장된 프로필로 판단한다. */
@Service
@Transactional
public class FamilyService {
    private final FamilyRepository families;
    private final ProfileSummaries summaries;
    private final IdentityClock clock;

    public FamilyService(FamilyRepository families, ProfileSummaries summaries, IdentityClock clock) {
        this.families = families;
        this.summaries = summaries;
        this.clock = clock;
    }

    /** 만든 사람은 항상 owner PARENT. 가족과 프로필 두 INSERT 는 한 트랜잭션. */
    public CreatedFamily createFamily(UUID userId, String familyName, String ownerName, LocalDate birthDate, Sex sex) {
        if (!families.profilesOfUser(userId).isEmpty()) throw new AlreadyInFamilyException();
        if (birthDate.isAfter(clock.today())) throw new IllegalArgumentException("생년월일은 미래일 수 없다");
        Family family = families.save(Family.createWithParent(userId, familyName, ownerName, birthDate, sex));
        return new CreatedFamily(family.getId(), family.getName(), summaries.summary(single(family.getProfiles())));
    }

    /** 키 · 몸무게는 가입 때 적은 값이다. 프로필에만 두고 요약(응답)에는 싣지 않는다. */
    public ProfileSummary addMember(
            UUID userId,
            UUID familyId,
            String name,
            LocalDate birthDate,
            Sex sex,
            ProfileRole role,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            @Nullable GuardianConsent guardianConsent) {
        Family family = load(familyId);
        Profile profile = family.addMember(
                userId, name, birthDate, sex, role, heightCm, weightKg, guardianConsent, clock.now(), clock.today());
        families.save(family);
        return summaries.summary(profile);
    }

    @Transactional(readOnly = true)
    public FamilyProfiles profilesOf(UUID userId, UUID familyId) {
        Family family = load(familyId);
        family.requireMember(userId);
        return new FamilyProfiles(
                family.getId(),
                family.getName(),
                family.getProfiles().stream().map(summaries::summary).toList());
    }

    private Family load(UUID familyId) {
        Family family = families.findById(familyId);
        if (family == null) throw new FamilyNotFoundException(familyId);
        return family;
    }

    private static Profile single(java.util.List<Profile> profiles) {
        if (profiles.size() != 1) throw new IllegalArgumentException("프로필이 하나가 아니다: " + profiles.size());
        return profiles.getFirst();
    }
}
