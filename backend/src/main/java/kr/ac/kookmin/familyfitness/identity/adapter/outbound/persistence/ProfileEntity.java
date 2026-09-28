package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "profiles")
public class ProfileEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "user_id")
    private @Nullable UUID userId;

    @Column(name = "display_name", nullable = false, length = 30)
    private String displayName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "sex", nullable = false, length = 1)
    private String sex;

    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "is_owner", nullable = false)
    private boolean isOwner;

    @Column(name = "height_cm", precision = 4, scale = 1)
    private @Nullable BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 4, scale = 1)
    private @Nullable BigDecimal weightKg;

    @Column(name = "support_mode", length = 20)
    private @Nullable String supportMode;

    @Column(name = "claim_code", length = 8)
    private @Nullable String claimCode;

    @Column(name = "claim_code_expires_at")
    private @Nullable Instant claimCodeExpiresAt;

    @Column(name = "claim_code_claimed_at")
    private @Nullable Instant claimCodeClaimedAt;

    @Column(name = "claim_code_issued_by")
    private @Nullable UUID claimCodeIssuedBy;

    @Column(name = "consent_personal_at")
    private @Nullable Instant consentPersonalAt;

    @Column(name = "consent_health_at")
    private @Nullable Instant consentHealthAt;

    @Column(name = "consent_by_user_id")
    private @Nullable UUID consentByUserId;

    @Column(name = "consent_revoked_at")
    private @Nullable Instant consentRevokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfileEntity() {}

    public ProfileEntity(
            UUID id,
            UUID familyId,
            @Nullable UUID userId,
            String displayName,
            LocalDate birthDate,
            String sex,
            String role,
            boolean isOwner,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            @Nullable String supportMode,
            @Nullable String claimCode,
            @Nullable Instant claimCodeExpiresAt,
            @Nullable Instant claimCodeClaimedAt,
            @Nullable UUID claimCodeIssuedBy,
            @Nullable Instant consentPersonalAt,
            @Nullable Instant consentHealthAt,
            @Nullable UUID consentByUserId,
            @Nullable Instant consentRevokedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.userId = userId;
        this.displayName = displayName;
        this.birthDate = birthDate;
        this.sex = sex;
        this.role = role;
        this.isOwner = isOwner;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.supportMode = supportMode;
        this.claimCode = claimCode;
        this.claimCodeExpiresAt = claimCodeExpiresAt;
        this.claimCodeClaimedAt = claimCodeClaimedAt;
        this.claimCodeIssuedBy = claimCodeIssuedBy;
        this.consentPersonalAt = consentPersonalAt;
        this.consentHealthAt = consentHealthAt;
        this.consentByUserId = consentByUserId;
        this.consentRevokedAt = consentRevokedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getSex() {
        return sex;
    }

    public String getRole() {
        return role;
    }

    public boolean isOwner() {
        return isOwner;
    }

    public @Nullable BigDecimal getHeightCm() {
        return heightCm;
    }

    public @Nullable BigDecimal getWeightKg() {
        return weightKg;
    }

    public @Nullable String getSupportMode() {
        return supportMode;
    }

    public void setSupportMode(@Nullable String supportMode) {
        this.supportMode = supportMode;
    }

    public @Nullable String getClaimCode() {
        return claimCode;
    }

    public void setClaimCode(@Nullable String claimCode) {
        this.claimCode = claimCode;
    }

    public @Nullable Instant getClaimCodeExpiresAt() {
        return claimCodeExpiresAt;
    }

    public void setClaimCodeExpiresAt(@Nullable Instant claimCodeExpiresAt) {
        this.claimCodeExpiresAt = claimCodeExpiresAt;
    }

    public @Nullable Instant getClaimCodeClaimedAt() {
        return claimCodeClaimedAt;
    }

    public void setClaimCodeClaimedAt(@Nullable Instant claimCodeClaimedAt) {
        this.claimCodeClaimedAt = claimCodeClaimedAt;
    }

    public @Nullable UUID getClaimCodeIssuedBy() {
        return claimCodeIssuedBy;
    }

    public void setClaimCodeIssuedBy(@Nullable UUID claimCodeIssuedBy) {
        this.claimCodeIssuedBy = claimCodeIssuedBy;
    }

    public @Nullable Instant getConsentPersonalAt() {
        return consentPersonalAt;
    }

    public void setConsentPersonalAt(@Nullable Instant consentPersonalAt) {
        this.consentPersonalAt = consentPersonalAt;
    }

    public @Nullable Instant getConsentHealthAt() {
        return consentHealthAt;
    }

    public void setConsentHealthAt(@Nullable Instant consentHealthAt) {
        this.consentHealthAt = consentHealthAt;
    }

    public @Nullable UUID getConsentByUserId() {
        return consentByUserId;
    }

    public void setConsentByUserId(@Nullable UUID consentByUserId) {
        this.consentByUserId = consentByUserId;
    }

    public @Nullable Instant getConsentRevokedAt() {
        return consentRevokedAt;
    }

    public void setConsentRevokedAt(@Nullable Instant consentRevokedAt) {
        this.consentRevokedAt = consentRevokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
