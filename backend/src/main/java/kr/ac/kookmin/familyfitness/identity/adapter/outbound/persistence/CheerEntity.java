package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "cheers")
public class CheerEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "from_profile_id", nullable = false)
    private UUID fromProfileId;

    @Column(name = "to_profile_id", nullable = false)
    private UUID toProfileId;

    @Column(name = "mission_id")
    private @Nullable UUID missionId;

    @Column(name = "emoji", length = 20)
    private @Nullable String emoji;

    @Column(name = "message", length = 200)
    private @Nullable String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheerEntity() {}

    public CheerEntity(
            UUID id,
            UUID familyId,
            UUID fromProfileId,
            UUID toProfileId,
            @Nullable UUID missionId,
            @Nullable String emoji,
            @Nullable String message,
            Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.fromProfileId = fromProfileId;
        this.toProfileId = toProfileId;
        this.missionId = missionId;
        this.emoji = emoji;
        this.message = message;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getFromProfileId() {
        return fromProfileId;
    }

    public UUID getToProfileId() {
        return toProfileId;
    }

    public @Nullable UUID getMissionId() {
        return missionId;
    }

    public @Nullable String getEmoji() {
        return emoji;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
