package kr.ac.kookmin.familyfitness.coaching.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/**
 * 한 가족: 부모(계정 있음) · 아이(계정 있음, 11세) · 응원만 하는 부모.
 * 아이를 더하거나({@link #addChild}) 보호자 동의를 거둔 상태({@link #withdrawConsent})로 바꿀 수 있다.
 * {@link FakeIdentity} 는 {@link #members()} 를 매번 읽으므로 바꾼 상태가 바로 반영된다.
 */
public class Family {
    public final UUID familyId;
    public final UUID parentUser = UUID.randomUUID();
    public final UUID childUser = UUID.randomUUID();
    public final UUID cheerParentUser = UUID.randomUUID();
    public final UUID outsiderUser = UUID.randomUUID();

    public final ProfileDetails parent;
    public final ProfileDetails child;
    public final ProfileDetails cheerParent;

    private final List<ProfileDetails> addedChildren = new ArrayList<>();
    private final Set<UUID> withdrawn = new HashSet<>();

    public Family() {
        this(UUID.randomUUID());
    }

    public Family(UUID familyId) {
        this.familyId = familyId;
        this.parent = details("엄마", ProfileRole.PARENT, LocalDate.of(1985, 3, 1), Sex.F, parentUser, SupportMode.FULL);
        this.child = details("민준", ProfileRole.CHILD, LocalDate.of(2015, 5, 20), Sex.M, childUser, null);
        this.cheerParent = details(
                "아빠", ProfileRole.PARENT, LocalDate.of(1983, 7, 7), Sex.M, cheerParentUser, SupportMode.CHEER_ONLY);
    }

    public List<ProfileDetails> members() {
        return Stream.concat(Stream.of(parent, child, cheerParent), addedChildren.stream())
                .map(it -> withdrawn.contains(it.profileId()) ? withConsentGiven(it, false) : it)
                .toList();
    }

    /** 계정 없는 아이를 더한다(동의 살아 있음). */
    public ProfileDetails addChild(String name, LocalDate birthDate) {
        ProfileDetails added = details(name, ProfileRole.CHILD, birthDate, Sex.F, null, null);
        addedChildren.add(added);
        return added;
    }

    /** 계정 없는 아이를 가입 때 적은 키(cm)와 몸무게(kg)와 함께 더한다(동의 살아 있음). */
    public ProfileDetails addChild(
            String name, LocalDate birthDate, @Nullable BigDecimal heightCm, @Nullable BigDecimal weightKg) {
        ProfileDetails added = new ProfileDetails(
                UUID.randomUUID(),
                familyId,
                null,
                name,
                ProfileRole.CHILD,
                birthDate,
                Sex.F,
                heightCm,
                weightKg,
                null,
                true);
        addedChildren.add(added);
        return added;
    }

    /** 보호자 동의를 거둔 상태(consentGiven=false)로 바꾼다. */
    public void withdrawConsent(UUID profileId) {
        withdrawn.add(profileId);
    }

    private ProfileDetails details(
            String name,
            ProfileRole role,
            LocalDate birthDate,
            Sex sex,
            @Nullable UUID userId,
            @Nullable SupportMode supportMode) {
        return new ProfileDetails(
                UUID.randomUUID(), familyId, userId, name, role, birthDate, sex, null, null, supportMode, true);
    }

    private static ProfileDetails withConsentGiven(ProfileDetails d, boolean consentGiven) {
        return new ProfileDetails(
                d.profileId(),
                d.familyId(),
                d.userId(),
                d.name(),
                d.role(),
                d.birthDate(),
                d.sex(),
                d.heightCm(),
                d.weightKg(),
                d.supportMode(),
                consentGiven);
    }
}
