package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyInviteRepository;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyInviteRepositoryAdapter implements FamilyInviteRepository {
    private final FamilyInviteJpaRepository rows;
    private final EntityManager em;

    public FamilyInviteRepositoryAdapter(FamilyInviteJpaRepository rows, EntityManager em) {
        this.rows = rows;
        this.em = em;
    }

    /** persist 뒤 곧바로 flush 해 코드가 겹치면 이 요청 안에서 유니크 위반으로 끝낸다. */
    @Override
    public void add(FamilyInvite invite) {
        GuardianConsent consent = invite.guardianConsent();
        em.persist(new FamilyInviteEntity(
                invite.code().code(),
                invite.familyId(),
                invite.role().name(),
                consent == null ? null : consent.personalData(),
                consent == null ? null : consent.healthData(),
                invite.consentByUserId(),
                invite.issuedByProfileId(),
                invite.createdAt(),
                invite.code().expiresAt(),
                invite.claimedAt(),
                invite.claimedByUserId()));
        rows.flush();
    }

    @Override
    public boolean isCodeTaken(String code) {
        return rows.existsById(code);
    }

    @Override
    public List<FamilyInvite> liveOf(UUID familyId, Instant now) {
        return rows.findByFamilyIdAndClaimedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescCodeAsc(familyId, now).stream()
                .map(FamilyInviteRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean deleteUnclaimed(UUID familyId, String code) {
        return rows.deleteUnclaimed(familyId, code) > 0;
    }

    static FamilyInvite toDomain(FamilyInviteEntity row) {
        Boolean personal = row.getConsentPersonalData();
        Boolean health = row.getConsentHealthData();
        GuardianConsent consent = personal == null || health == null ? null : new GuardianConsent(personal, health);
        return new FamilyInvite(
                new ClaimCode(row.getCode(), row.getExpiresAt()),
                row.getFamilyId(),
                ProfileRole.valueOf(row.getRole()),
                consent,
                row.getConsentByUserId(),
                row.getIssuedByProfileId(),
                row.getCreatedAt(),
                row.getClaimedAt(),
                row.getClaimedByUserId());
    }
}
