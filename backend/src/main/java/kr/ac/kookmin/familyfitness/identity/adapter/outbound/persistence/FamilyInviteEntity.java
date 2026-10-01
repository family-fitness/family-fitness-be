package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/* V170__identity_family_invites.sql 의 칸을 그대로 따른다. 사용 표시는 FamilyInviteJpaRepository 의 조건부 UPDATE 로만 한다. */
@Entity
@Table(name = "family_invites")
public class FamilyInviteEntity {
    @Id
    @Column(name = "code", nullable = false, length = 8)
    private String code;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "consent_personal_data")
    private @Nullable Boolean consentPersonalData;

    @Column(name = "consent_health_data")
    private @Nullable Boolean consentHealthData;

    @Column(name = "consent_by_user_id")
    private @Nullable UUID consentByUserId;

    @Column(name = "issued_by_profile_id", nullable = false)
    private UUID issuedByProfileId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private @Nullable Instant claimedAt;

    @Column(name = "claimed_by_user_id")
    private @Nullable UUID claimedByUserId;

    protected FamilyInviteEntity() {}

    public FamilyInviteEntity(
            String code,
            UUID familyId,
            String role,
            @Nullable Boolean consentPersonalData,
            @Nullable Boolean consentHealthData,
            @Nullable UUID consentByUserId,
            UUID issuedByProfileId,
            Instant createdAt,
            Instant expiresAt,
            @Nullable Instant claimedAt,
            @Nullable UUID claimedByUserId) {
        this.code = code;
        this.familyId = familyId;
        this.role = role;
        this.consentPersonalData = consentPersonalData;
        this.consentHealthData = consentHealthData;
        this.consentByUserId = consentByUserId;
        this.issuedByProfileId = issuedByProfileId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.claimedAt = claimedAt;
        this.claimedByUserId = claimedByUserId;
    }

    public String getCode() {
        return code;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getRole() {
        return role;
    }

    public @Nullable Boolean getConsentPersonalData() {
        return consentPersonalData;
    }

    public @Nullable Boolean getConsentHealthData() {
        return consentHealthData;
    }

    public @Nullable UUID getConsentByUserId() {
        return consentByUserId;
    }

    public UUID getIssuedByProfileId() {
        return issuedByProfileId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public @Nullable Instant getClaimedAt() {
        return claimedAt;
    }

    public @Nullable UUID getClaimedByUserId() {
        return claimedByUserId;
    }
}
