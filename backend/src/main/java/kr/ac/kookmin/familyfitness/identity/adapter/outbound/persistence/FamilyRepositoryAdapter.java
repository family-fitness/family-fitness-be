package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.ConsentRecord;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyRepositoryAdapter implements FamilyRepository {
    private final FamilyJpaRepository familyJpa;
    private final ProfileJpaRepository profileJpa;
    private final EntityManager em;
    private final Clock clock;

    public FamilyRepositoryAdapter(
            FamilyJpaRepository familyJpa, ProfileJpaRepository profileJpa, EntityManager em, Clock clock) {
        this.familyJpa = familyJpa;
        this.profileJpa = profileJpa;
        this.em = em;
        this.clock = clock;
    }

    @Override
    public List<UUID> allIds() {
        return familyJpa.findAll().stream().map(FamilyEntity::getId).toList();
    }

    @Override
    public @Nullable Family findById(UUID familyId) {
        return familyJpa.findById(familyId).map(this::toDomain).orElse(null);
    }

    @Override
    public @Nullable Family findByProfileId(UUID profileId) {
        UUID familyId =
                profileJpa.findById(profileId).map(ProfileEntity::getFamilyId).orElse(null);
        return familyId == null ? null : findById(familyId);
    }

    @Override
    public @Nullable Family findByClaimCode(String code) {
        ProfileEntity profile = profileJpa.findByClaimCode(code);
        return profile == null ? null : findById(profile.getFamilyId());
    }

    @Override
    public boolean isClaimCodeTaken(String code) {
        return profileJpa.existsByClaimCode(code);
    }

    @Override
    public List<Profile> profilesOfUser(UUID userId) {
        return profileJpa.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(FamilyRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Family save(Family family) {
        Instant now = clock.instant();
        FamilyEntity familyEntity = familyJpa.findById(family.getId()).orElse(null);
        if (familyEntity == null) {
            em.persist(new FamilyEntity(family.getId(), family.getName(), null, now, now));
        } else {
            familyEntity.setName(family.getName());
            familyEntity.setUpdatedAt(now);
        }
        Map<UUID, ProfileEntity> existing = profileJpa.findByFamilyIdOrderByCreatedAtAscIdAsc(family.getId()).stream()
                .collect(Collectors.toMap(ProfileEntity::getId, Function.identity(), (a, b) -> b));
        for (Profile profile : family.getProfiles()) {
            ProfileEntity entity = existing.get(profile.getId());
            if (entity == null) {
                em.persist(toNewEntity(profile, now));
            } else {
                applyChanges(entity, profile, now);
            }
        }
        return family;
    }

    @Override
    public boolean attachUserIfUnclaimed(UUID profileId, UUID userId, Instant at) {
        return profileJpa.attachUserIfUnclaimed(profileId, userId, at) == 1;
    }

    private Family toDomain(FamilyEntity entity) {
        return Family.restore(
                entity.getId(),
                entity.getName(),
                profileJpa.findByFamilyIdOrderByCreatedAtAscIdAsc(entity.getId()).stream()
                        .map(FamilyRepositoryAdapter::toDomain)
                        .toList());
    }

    private static Profile toDomain(ProfileEntity entity) {
        ClaimCode claimCode = null;
        if (entity.getClaimCode() != null) {
            Instant expiresAt = entity.getClaimCodeExpiresAt();
            if (expiresAt == null) throw new IllegalArgumentException("claim_code 와 expires_at 은 함께 있어야 한다");
            claimCode = new ClaimCode(entity.getClaimCode(), expiresAt);
        }
        return new Profile(
                entity.getId(),
                entity.getFamilyId(),
                entity.getUserId(),
                ProfileRole.valueOf(entity.getRole()),
                entity.isOwner(),
                entity.getDisplayName(),
                entity.getBirthDate(),
                Sex.valueOf(entity.getSex()),
                entity.getHeightCm(),
                entity.getWeightKg(),
                entity.getSupportMode() == null ? null : SupportMode.valueOf(entity.getSupportMode()),
                claimCode,
                entity.getClaimCodeClaimedAt(),
                new ConsentRecord(
                        entity.getConsentPersonalAt(),
                        entity.getConsentHealthAt(),
                        entity.getConsentByUserId(),
                        entity.getConsentRevokedAt()));
    }

    private static ProfileEntity toNewEntity(Profile profile, Instant now) {
        ClaimCode claimCode = profile.getClaimCode();
        return new ProfileEntity(
                profile.getId(),
                profile.getFamilyId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getBirthDate(),
                profile.getSex().name(),
                profile.getRole().name(),
                profile.isOwner(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getSupportMode() == null
                        ? null
                        : profile.getSupportMode().name(),
                claimCode == null ? null : claimCode.code(),
                claimCode == null ? null : claimCode.expiresAt(),
                profile.getClaimCodeClaimedAt(),
                profile.getConsent().personalAt(),
                profile.getConsent().healthAt(),
                profile.getConsent().byUserId(),
                profile.getConsent().revokedAt(),
                now,
                now);
    }

    /** 생성 뒤 바뀔 수 있는 값만 덮어쓴다. 계정 연결(user_id)은 조건부 UPDATE 로만 바꾼다. */
    private static void applyChanges(ProfileEntity entity, Profile profile, Instant now) {
        ClaimCode claimCode = profile.getClaimCode();
        entity.setSupportMode(
                profile.getSupportMode() == null
                        ? null
                        : profile.getSupportMode().name());
        entity.setClaimCode(claimCode == null ? null : claimCode.code());
        entity.setClaimCodeExpiresAt(claimCode == null ? null : claimCode.expiresAt());
        entity.setClaimCodeClaimedAt(profile.getClaimCodeClaimedAt());
        entity.setConsentPersonalAt(profile.getConsent().personalAt());
        entity.setConsentHealthAt(profile.getConsent().healthAt());
        entity.setConsentByUserId(profile.getConsent().byUserId());
        entity.setConsentRevokedAt(profile.getConsent().revokedAt());
        entity.setUpdatedAt(now);
    }
}
