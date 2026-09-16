package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/** 한 가족: 부모(계정 있음) · 아이(계정 있음, 11세) · 응원만 하는 부모. */
public class Family {
    public final UUID familyId;
    public final UUID parentUser = UUID.randomUUID();
    public final UUID childUser = UUID.randomUUID();
    public final UUID cheerParentUser = UUID.randomUUID();
    public final UUID outsiderUser = UUID.randomUUID();

    public final ProfileDetails parent;
    public final ProfileDetails child;
    public final ProfileDetails cheerParent;

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
        return List.of(parent, child, cheerParent);
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
}
