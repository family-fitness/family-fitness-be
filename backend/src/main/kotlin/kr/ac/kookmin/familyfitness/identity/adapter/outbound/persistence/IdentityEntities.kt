package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/*
 * JPA 엔티티는 V1__init.sql 의 컬럼을 그대로 따른다(ddl-auto=validate). 연관관계 대신 ID 컬럼을 들고,
 * 애그리게잇 조립은 FamilyRepositoryAdapter 가 한다. 열거형은 varchar 로 저장하고 매퍼가 변환한다.
 */

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "provider", nullable = false, length = 20)
    var provider: String,
    @Column(name = "provider_user_id", nullable = false, length = 191)
    var providerUserId: String,
    @Column(name = "email", length = 255)
    var email: String?,
    @Column(name = "status", nullable = false, length = 20)
    var status: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

@Entity
@Table(name = "families")
class FamilyEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "name", nullable = false, length = 60)
    var name: String,
    @Column(name = "region_code", length = 20)
    var regionCode: String?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

@Entity
@Table(name = "profiles")
class ProfileEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "family_id", nullable = false)
    var familyId: UUID,
    @Column(name = "user_id")
    var userId: UUID?,
    @Column(name = "display_name", nullable = false, length = 30)
    var displayName: String,
    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate,
    @Column(name = "sex", nullable = false, length = 1)
    var sex: String,
    @Column(name = "role", nullable = false, length = 10)
    var role: String,
    @Column(name = "is_owner", nullable = false)
    var isOwner: Boolean,
    @Column(name = "height_cm", precision = 4, scale = 1)
    var heightCm: BigDecimal?,
    @Column(name = "weight_kg", precision = 4, scale = 1)
    var weightKg: BigDecimal?,
    @Column(name = "support_mode", length = 20)
    var supportMode: String?,
    @Column(name = "claim_code", length = 8)
    var claimCode: String?,
    @Column(name = "claim_code_expires_at")
    var claimCodeExpiresAt: Instant?,
    @Column(name = "claim_code_claimed_at")
    var claimCodeClaimedAt: Instant?,
    @Column(name = "consent_personal_at")
    var consentPersonalAt: Instant?,
    @Column(name = "consent_health_at")
    var consentHealthAt: Instant?,
    @Column(name = "consent_by_user_id")
    var consentByUserId: UUID?,
    @Column(name = "consent_revoked_at")
    var consentRevokedAt: Instant?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

@Entity
@Table(name = "cheers")
class CheerEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "family_id", nullable = false)
    var familyId: UUID,
    @Column(name = "from_profile_id", nullable = false)
    var fromProfileId: UUID,
    @Column(name = "to_profile_id", nullable = false)
    var toProfileId: UUID,
    @Column(name = "mission_id")
    var missionId: UUID?,
    @Column(name = "emoji", length = 20)
    var emoji: String?,
    @Column(name = "message", length = 200)
    var message: String?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
)
