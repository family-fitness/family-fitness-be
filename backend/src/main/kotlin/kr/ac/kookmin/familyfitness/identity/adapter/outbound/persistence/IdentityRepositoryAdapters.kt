package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence

import jakarta.persistence.EntityManager
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository
import kr.ac.kookmin.familyfitness.identity.domain.Cheer
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode
import kr.ac.kookmin.familyfitness.identity.domain.ConsentRecord
import kr.ac.kookmin.familyfitness.identity.domain.Family
import kr.ac.kookmin.familyfitness.identity.domain.Profile
import kr.ac.kookmin.familyfitness.identity.domain.User
import kr.ac.kookmin.familyfitness.identity.domain.UserStatus
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Repository
class UserRepositoryAdapter(
    private val jpa: UserJpaRepository,
    private val em: EntityManager,
    private val clock: Clock,
) : UserRepository {
    override fun findById(id: UUID): User? = jpa.findById(id).map { it.toDomain() }.orElse(null)

    override fun findByProviderAndProviderUserId(
        provider: String,
        providerUserId: String,
    ): User? = jpa.findByProviderAndProviderUserId(provider, providerUserId)?.toDomain()

    override fun save(user: User): User {
        val now = clock.instant()
        val existing = jpa.findById(user.id).orElse(null)
        if (existing == null) {
            em.persist(UserEntity(user.id, user.provider, user.providerUserId, user.email, user.status.name, now, now))
        } else {
            existing.email = user.email
            existing.status = user.status.name
            existing.updatedAt = now
        }
        return user
    }

    private fun UserEntity.toDomain() = User(id, provider, providerUserId, email, UserStatus.valueOf(status))
}

@Repository
class FamilyRepositoryAdapter(
    private val familyJpa: FamilyJpaRepository,
    private val profileJpa: ProfileJpaRepository,
    private val em: EntityManager,
    private val clock: Clock,
) : FamilyRepository {
    override fun findById(familyId: UUID): Family? = familyJpa.findById(familyId).map { it.toDomain() }.orElse(null)

    override fun findByProfileId(profileId: UUID): Family? =
        profileJpa
            .findById(profileId)
            .map { it.familyId }
            .orElse(null)
            ?.let(::findById)

    override fun findByClaimCode(code: String): Family? = profileJpa.findByClaimCode(code)?.familyId?.let(::findById)

    override fun isClaimCodeTaken(code: String): Boolean = profileJpa.existsByClaimCode(code)

    override fun profilesOfUser(userId: UUID): List<Profile> = profileJpa.findByUserIdOrderByCreatedAtAscIdAsc(userId).map { it.toDomain() }

    override fun save(family: Family): Family {
        val now = clock.instant()
        val familyEntity = familyJpa.findById(family.id).orElse(null)
        if (familyEntity == null) {
            em.persist(FamilyEntity(family.id, family.name, null, now, now))
        } else {
            familyEntity.name = family.name
            familyEntity.updatedAt = now
        }
        val existing = profileJpa.findByFamilyIdOrderByCreatedAtAscIdAsc(family.id).associateBy { it.id }
        family.profiles.forEach { profile ->
            val entity = existing[profile.id]
            if (entity == null) {
                em.persist(profile.toNewEntity(now))
            } else {
                entity.applyChanges(profile, now)
            }
        }
        return family
    }

    override fun attachUserIfUnclaimed(
        profileId: UUID,
        userId: UUID,
        at: Instant,
    ): Boolean = profileJpa.attachUserIfUnclaimed(profileId, userId, at) == 1

    private fun FamilyEntity.toDomain(): Family =
        Family.restore(id, name, profileJpa.findByFamilyIdOrderByCreatedAtAscIdAsc(id).map { it.toDomain() })

    private fun ProfileEntity.toDomain(): Profile =
        Profile(
            id = id,
            familyId = familyId,
            userId = userId,
            role = ProfileRole.valueOf(role),
            isOwner = isOwner,
            displayName = displayName,
            birthDate = birthDate,
            sex = Sex.valueOf(sex),
            heightCm = heightCm,
            weightKg = weightKg,
            supportMode = supportMode?.let(SupportMode::valueOf),
            claimCode = claimCode?.let { ClaimCode(it, requireNotNull(claimCodeExpiresAt) { "claim_code 와 expires_at 은 함께 있어야 한다" }) },
            claimCodeClaimedAt = claimCodeClaimedAt,
            consent = ConsentRecord(consentPersonalAt, consentHealthAt, consentByUserId, consentRevokedAt),
        )

    private fun Profile.toNewEntity(now: Instant): ProfileEntity =
        ProfileEntity(
            id = id,
            familyId = familyId,
            userId = userId,
            displayName = displayName,
            birthDate = birthDate,
            sex = sex.name,
            role = role.name,
            isOwner = isOwner,
            heightCm = heightCm,
            weightKg = weightKg,
            supportMode = supportMode?.name,
            claimCode = claimCode?.code,
            claimCodeExpiresAt = claimCode?.expiresAt,
            claimCodeClaimedAt = claimCodeClaimedAt,
            consentPersonalAt = consent.personalAt,
            consentHealthAt = consent.healthAt,
            consentByUserId = consent.byUserId,
            consentRevokedAt = consent.revokedAt,
            createdAt = now,
            updatedAt = now,
        )

    /** 생성 뒤 바뀔 수 있는 값만 덮어쓴다. 계정 연결(user_id)은 조건부 UPDATE 로만 바꾼다. */
    private fun ProfileEntity.applyChanges(
        profile: Profile,
        now: Instant,
    ) {
        supportMode = profile.supportMode?.name
        claimCode = profile.claimCode?.code
        claimCodeExpiresAt = profile.claimCode?.expiresAt
        claimCodeClaimedAt = profile.claimCodeClaimedAt
        consentPersonalAt = profile.consent.personalAt
        consentHealthAt = profile.consent.healthAt
        consentByUserId = profile.consent.byUserId
        consentRevokedAt = profile.consent.revokedAt
        updatedAt = now
    }
}

@Repository
class CheerRepositoryAdapter(
    private val jpa: CheerJpaRepository,
    private val em: EntityManager,
) : CheerRepository {
    override fun save(cheer: Cheer): Cheer {
        em.persist(
            CheerEntity(
                id = cheer.id,
                familyId = cheer.familyId,
                fromProfileId = cheer.fromProfileId,
                toProfileId = cheer.toProfileId,
                missionId = cheer.missionId,
                emoji = cheer.emoji,
                message = cheer.message,
                createdAt = cheer.createdAt,
            ),
        )
        return cheer
    }

    override fun countFromTo(
        fromProfileId: UUID,
        toProfileId: UUID,
        after: Instant,
    ): Int = jpa.countByFromProfileIdAndToProfileIdAndCreatedAtAfter(fromProfileId, toProfileId, after).toInt()

    override fun countInFamily(
        familyId: UUID,
        from: Instant,
        to: Instant,
    ): Int = jpa.countByFamilyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(familyId, from, to).toInt()
}
