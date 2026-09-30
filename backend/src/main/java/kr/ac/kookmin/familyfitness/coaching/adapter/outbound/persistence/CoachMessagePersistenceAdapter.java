package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachMessageRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class CoachMessagePersistenceAdapter implements CoachMessageRepository {
    private final CoachMessageJpaRepository messages;
    private final CoachMessageCitationJpaRepository citations;

    public CoachMessagePersistenceAdapter(
            CoachMessageJpaRepository messages, CoachMessageCitationJpaRepository citations) {
        this.messages = messages;
        this.citations = citations;
    }

    @Override
    public CoachMessage save(CoachMessage message) {
        messages.save(new CoachMessageEntity(
                message.getId(),
                message.getConversationId(),
                message.getProfileId(),
                message.getRole().name(),
                message.getContent(),
                message.isRefused(),
                message.getRefusalReason(),
                message.getCreatedAt()));
        citations.saveAll(message.getCitations().stream()
                .map(it -> new CoachMessageCitationEntity(
                        new CoachMessageCitationId(message.getId(), it.index()),
                        it.chunkId() == null ? null : take(it.chunkId(), 200),
                        take(it.sourceLabel(), 200),
                        take(it.excerpt(), 500),
                        it.url() == null ? null : take(it.url(), 500)))
                .toList());
        return message;
    }

    @Override
    public @Nullable UUID ownerOfConversation(UUID conversationId) {
        CoachMessageEntity first = messages.findFirstByConversationIdOrderByCreatedAtAsc(conversationId);
        return first == null ? null : first.getProfileId();
    }

    private static String take(String value, int n) {
        return value.length() <= n ? value : value.substring(0, n);
    }
}
