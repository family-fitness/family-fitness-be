package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class ConversationNotFoundException extends DomainException {
    public ConversationNotFoundException(UUID conversationId) {
        super("CONVERSATION_NOT_FOUND", ErrorKind.NOT_FOUND, "대화가 없습니다: " + conversationId);
    }
}
