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

    /** CheerKind 이름(DONE · PRAISE · THANKS). */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "mission_id")
    private @Nullable UUID missionId;

    @Column(name = "sticker_id", length = 20)
    private @Nullable String stickerId;

    @Column(name = "message", length = 200)
    private @Nullable String message;

    @Column(name = "reply_to_cheer_id")
    private @Nullable UUID replyToCheerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheerEntity() {}

    public CheerEntity(
            UUID id,
            UUID familyId,
            UUID fromProfileId,
            UUID toProfileId,
            String kind,
            @Nullable UUID missionId,
            @Nullable String stickerId,
            @Nullable String message,
            @Nullable UUID replyToCheerId,
            Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.fromProfileId = fromProfileId;
        this.toProfileId = toProfileId;
        this.kind = kind;
        this.missionId = missionId;
        this.stickerId = stickerId;
        this.message = message;
        this.replyToCheerId = replyToCheerId;
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

    public String getKind() {
        return kind;
    }

    public @Nullable UUID getMissionId() {
        return missionId;
    }

    public @Nullable String getStickerId() {
        return stickerId;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public @Nullable UUID getReplyToCheerId() {
        return replyToCheerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
