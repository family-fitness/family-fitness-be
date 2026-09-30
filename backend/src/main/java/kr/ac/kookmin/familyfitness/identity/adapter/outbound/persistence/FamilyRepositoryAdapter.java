package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyInFamilyException;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.ConcurrentFamilyChangeException;
import kr.ac.kookmin.familyfitness.identity.domain.ConsentEvent;
import kr.ac.kookmin.familyfitness.identity.domain.ConsentRecord;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyRepositoryAdapter implements FamilyRepository {
    /** V143 — 한 계정은 프로필 하나에만 붙는다. */
    private static final String ONE_FAMILY_INDEX = "uq_profiles_user";

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

    /** 가족 행 한 번, 프로필 행 한 번(IN)으로 읽어 가족마다 묶는다. 프로필 차례는 {@link #findById} 와 같다(만든 시각 · id). */
    @Override
    public List<Family> findAllById(Collection<UUID> familyIds) {
        if (familyIds.isEmpty()) return List.of();
        Map<UUID, List<Profile>> profiles = profileJpa.findByFamilyIdInOrderByCreatedAtAscIdAsc(familyIds).stream()
                .collect(Collectors.groupingBy(
                        ProfileEntity::getFamilyId,
                        Collectors.mapping(FamilyRepositoryAdapter::toDomain, Collectors.toList())));
        return familyJpa.findAllById(familyIds).stream()
                .map(it -> Family.restore(it.getId(), it.getName(), profiles.getOrDefault(it.getId(), List.of())))
                .toList();
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
        // 식구 차례는 만든 시각 · id 다. 시계가 같은 시각을 두 번 주면 id(무작위)로 갈려 차례가 바뀌므로,
        // 새 식구의 만든 시각은 그 가족의 마지막 식구보다 늘 1µs 이상 뒤로 둔다.
        Instant last = existing.values().stream()
                .map(ProfileEntity::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MIN);
        for (Profile profile : family.getProfiles()) {
            ProfileEntity entity = existing.get(profile.getId());
            if (entity == null) {
                Instant createdAt = now.isAfter(last) ? now : last.plus(1, ChronoUnit.MICROS);
                last = createdAt;
                em.persist(toNewEntity(profile, createdAt, now));
            } else {
                applyChanges(entity, profile, now);
            }
        }
        try {
            profileJpa.flush();
        } catch (DataIntegrityViolationException e) {
            throw translated(e);
        } catch (OptimisticLockingFailureException e) {
            // 읽은 뒤 다른 요청이 같은 프로필 행을 먼저 바꿔 커밋했다(행 버전이 다르다). 옛 값으로 덮지 않고 409 로 끝낸다.
            throw new ConcurrentFamilyChangeException(e);
        }
        // 프로필 행이 먼저 있어야 이력의 FK 가 맞는다 — 위 flush 뒤에 넣는다.
        for (ConsentEvent event : family.drainConsentEvents()) {
            em.persist(new ConsentEventEntity(
                    event.profileId(),
                    event.actorUserId(),
                    event.kind().name(),
                    event.personalData(),
                    event.healthData(),
                    event.occurredAt()));
        }
        return family;
    }

    @Override
    public boolean attachUserIfUnclaimed(UUID profileId, UUID userId, Instant at) {
        try {
            return profileJpa.attachUserIfUnclaimed(profileId, userId, at) == 1;
        } catch (DataIntegrityViolationException e) {
            throw translated(e);
        }
    }

    /**
     * 곧바로 flush 해 유니크 위반을 여기서 받는다(flush · 수정 쿼리는 Spring Data 프록시를 거쳐 예외가 번역된다).
     * uq_profiles_user 위반은 사전 검사(profilesOfUser)를 함께 지나친 동시 요청이 같은 계정을 두 프로필에 붙이려 한 것이라
     * ALREADY_IN_FAMILY 로 바꾼다. 다른 제약 위반은 그대로 던진다. 위반 뒤 트랜잭션은 롤백 전용이 되므로 예외로 끝낸다.
     */
    private static RuntimeException translated(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains(ONE_FAMILY_INDEX)) {
            return new AlreadyInFamilyException("다른 가족에 이미 프로필이 있습니다");
        }
        return e;
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
                entity.getClaimCodeIssuedBy(),
                new ConsentRecord(
                        entity.getConsentPersonalAt(),
                        entity.getConsentHealthAt(),
                        entity.getConsentByUserId(),
                        entity.getConsentRevokedAt()));
    }

    private static ProfileEntity toNewEntity(Profile profile, Instant createdAt, Instant now) {
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
                profile.getClaimCodeIssuedBy(),
                profile.getConsent().personalAt(),
                profile.getConsent().healthAt(),
                profile.getConsent().byUserId(),
                profile.getConsent().revokedAt(),
                createdAt,
                now);
    }

    /**
     * 생성 뒤 바뀔 수 있는 값만 덮어쓴다. 계정 연결(user_id)은 조건부 UPDATE 로만 바꾼다.
     * Hibernate 는 바뀐 행의 모든 칸을 다시 쓰므로(user_id 포함) 옛 상태로 덮는 것은 행 버전({@link ProfileEntity} @Version)이 막는다.
     */
    private static void applyChanges(ProfileEntity entity, Profile profile, Instant now) {
        ClaimCode claimCode = profile.getClaimCode();
        entity.setDisplayName(profile.getDisplayName());
        entity.setBirthDate(profile.getBirthDate());
        entity.setSex(profile.getSex().name());
        entity.setSupportMode(
                profile.getSupportMode() == null
                        ? null
                        : profile.getSupportMode().name());
        entity.setClaimCode(claimCode == null ? null : claimCode.code());
        entity.setClaimCodeExpiresAt(claimCode == null ? null : claimCode.expiresAt());
        entity.setClaimCodeClaimedAt(profile.getClaimCodeClaimedAt());
        entity.setClaimCodeIssuedBy(profile.getClaimCodeIssuedBy());
        entity.setConsentPersonalAt(profile.getConsent().personalAt());
        entity.setConsentHealthAt(profile.getConsent().healthAt());
        entity.setConsentByUserId(profile.getConsent().byUserId());
        entity.setConsentRevokedAt(profile.getConsent().revokedAt());
        entity.setUpdatedAt(now);
    }
}
