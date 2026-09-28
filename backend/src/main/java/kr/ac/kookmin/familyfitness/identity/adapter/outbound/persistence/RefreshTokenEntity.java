package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/* V138__refresh_tokens.sql 의 컬럼을 그대로 따른다. 폐기는 RefreshTokenJpaRepository 의 UPDATE 문으로만 한다. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id
    @Column(name = "jti", nullable = false)
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    @Column(name = "replaced_by")
    private @Nullable UUID replacedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {}

    public RefreshTokenEntity(
            UUID jti,
            UUID userId,
            UUID familyId,
            Instant expiresAt,
            @Nullable Instant revokedAt,
            @Nullable UUID replacedBy,
            Instant createdAt) {
        this.jti = jti;
        this.userId = userId;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
        this.createdAt = createdAt;
    }

    public UUID getJti() {
        return jti;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public @Nullable Instant getRevokedAt() {
        return revokedAt;
    }

    public @Nullable UUID getReplacedBy() {
        return replacedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
