package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage;
import org.jspecify.annotations.Nullable;

public interface CoachMessageRepository {
    CoachMessage save(CoachMessage message);

    /** 대화의 주인 프로필. 대화가 없으면 null. */
    @Nullable
    UUID ownerOfConversation(UUID conversationId);
}
