package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 코치 대화 메시지. 한 대화(conversationId)는 한 프로필의 것이다.
 * 거부(refused)도 오류가 아니라 메시지로 저장한다.
 */
public class CoachMessage {
    /** refused=false 인데 인용이 0개인 답변은 근거 없는 답이므로 서버가 거부로 바꾼다. */
    public static final String NO_CITATION_GENERATED = "no_citation_generated";

    private final UUID id;
    private final UUID conversationId;
    private final UUID profileId;
    private final MessageRole role;
    private final String content;
    private final boolean refused;
    private final @Nullable String refusalReason;
    private final List<MessageCitation> citations;
    private final Instant createdAt;

    private CoachMessage(
            UUID id,
            UUID conversationId,
            UUID profileId,
            MessageRole role,
            String content,
            boolean refused,
            @Nullable String refusalReason,
            List<MessageCitation> citations,
            Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.profileId = profileId;
        this.role = role;
        this.content = content;
        this.refused = refused;
        this.refusalReason = refusalReason;
        this.citations = citations;
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

    public MessageRole getRole() {
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

    public List<MessageCitation> getCitations() {
        return citations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static CoachMessage user(UUID id, UUID conversationId, UUID profileId, String question, Instant at) {
        return new CoachMessage(id, conversationId, profileId, MessageRole.USER, question, false, null, List.of(), at);
    }

    public static CoachMessage assistant(
            UUID id,
            UUID conversationId,
            UUID profileId,
            String answer,
            List<MessageCitation> citations,
            boolean refused,
            @Nullable String refusalReason,
            Instant at) {
        boolean uncited = !refused && citations.isEmpty();
        return new CoachMessage(
                id,
                conversationId,
                profileId,
                MessageRole.ASSISTANT,
                answer,
                refused || uncited,
                uncited ? NO_CITATION_GENERATED : refusalReason,
                refused ? List.of() : citations,
                at);
    }

    public static CoachMessage reconstitute(
            UUID id,
            UUID conversationId,
            UUID profileId,
            MessageRole role,
            String content,
            boolean refused,
            @Nullable String refusalReason,
            List<MessageCitation> citations,
            Instant createdAt) {
        return new CoachMessage(
                id, conversationId, profileId, role, content, refused, refusalReason, citations, createdAt);
    }
}
