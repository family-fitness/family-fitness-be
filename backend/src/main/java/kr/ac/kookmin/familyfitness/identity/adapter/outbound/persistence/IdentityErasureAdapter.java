package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.IdentityErasureRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class IdentityErasureAdapter implements IdentityErasureRepository {
    private final IdentityErasureJpaRepository rows;

    public IdentityErasureAdapter(IdentityErasureJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public void lockFamily(UUID familyId) {
        rows.lockFamily(familyId);
    }

    @Override
    public List<UUID> cheersOf(UUID profileId) {
        List<UUID> own = rows.findCheersOf(profileId);
        if (own.isEmpty()) return own;
        Set<UUID> all = new LinkedHashSet<>(own);
        all.addAll(rows.findRepliesTo(own));
        return List.copyOf(all);
    }

    @Override
    public List<UUID> missionsOnCheers(UUID familyId) {
        return rows.findMissionsOnCheers(familyId);
    }

    @Override
    public void forgetMissionOnCheers(UUID familyId, UUID missionId) {
        rows.forgetMissionOnCheers(familyId, missionId);
    }

    /** 고마워요가 지울 응원을 가리키므로(fk_cheers_reply_to) 답장 연결을 먼저 끊는다. */
    @Override
    public void eraseProfile(UUID profileId, UUID heirProfileId, Collection<UUID> cheerIds) {
        if (!cheerIds.isEmpty()) {
            rows.unlinkReplies(cheerIds);
            rows.deleteCheers(cheerIds);
        }
        rows.deleteSlots(List.of(profileId));
        rows.handOverSlots(profileId, heirProfileId);
        rows.deleteConsentEvents(List.of(profileId));
        rows.handOverInvites(profileId, heirProfileId);
        rows.deleteProfile(profileId);
    }

    @Override
    public void forgetConsentActor(UUID userId) {
        rows.forgetConsentGiver(userId);
        rows.forgetConsentEventActor(userId);
    }

    @Override
    public void eraseFamily(UUID familyId, Collection<UUID> profileIds) {
        rows.unlinkRepliesOfFamily(familyId);
        rows.deleteCheersOfFamily(familyId);
        if (!profileIds.isEmpty()) {
            rows.deleteSlots(profileIds);
            rows.deleteConsentEvents(profileIds);
        }
        rows.forgetInvitersOfFamily(familyId);
        rows.deleteProfilesOfFamily(familyId);
        rows.deleteFamily(familyId);
    }

    @Override
    public void eraseAccount(UUID userId) {
        rows.deleteRefreshTokens(userId);
        forgetConsentActor(userId);
        rows.deleteUser(userId);
    }
}
