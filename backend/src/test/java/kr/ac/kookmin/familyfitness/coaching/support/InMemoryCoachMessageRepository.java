package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachMessageRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage;
import org.jspecify.annotations.Nullable;

public class InMemoryCoachMessageRepository implements CoachMessageRepository {
    public final List<CoachMessage> messages = new ArrayList<>();

    @Override
    public CoachMessage save(CoachMessage message) {
        messages.add(message);
        return message;
    }

    @Override
    public @Nullable UUID ownerOfConversation(UUID conversationId) {
        return messages.stream()
                .filter(it -> it.getConversationId().equals(conversationId))
                .min(Comparator.comparing(CoachMessage::getCreatedAt))
                .map(CoachMessage::getProfileId)
                .orElse(null);
    }
}
