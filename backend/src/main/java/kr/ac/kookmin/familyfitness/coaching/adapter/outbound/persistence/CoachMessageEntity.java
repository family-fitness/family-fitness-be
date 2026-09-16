package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "coach_messages")
public class CoachMessageEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "refused", nullable = false)
    private boolean refused;

    @Column(name = "refusal_reason", length = 40)
    private @Nullable String refusalReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CoachMessageEntity() {}

    public CoachMessageEntity(
            UUID id,
            UUID conversationId,
            UUID profileId,
            String role,
            String content,
            boolean refused,
            @Nullable String refusalReason,
            Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.profileId = profileId;
        this.role = role;
        this.content = content;
        this.refused = refused;
        this.refusalReason = refusalReason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public boolean isRefused() {
        return refused;
    }

    public @Nullable String getRefusalReason() {
        return refusalReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
