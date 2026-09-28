package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachMessageRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachingForbiddenException;
import kr.ac.kookmin.familyfitness.coaching.domain.ConversationNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MessageCitation;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageResponse;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 코치 대화. USER·ASSISTANT 메시지를 모두 저장한다(거부도 저장).
 * AI 호출은 트랜잭션 밖에서 하고, 성공했을 때만 두 메시지를 한 트랜잭션으로 저장한다 — AI 장애(503)면 아무것도 남지 않는다.
 * 누구 이름으로 묻는지는 칸 끝과 같다: 이 계정이 그 프로필 이름으로 할 수 있어야 한다(자기 프로필이거나, 보호자가 계정 없는 아이를
 * 대신할 때). 같은 가족이기만 하면 되던 때는 자녀 계정이 부모 이름으로 대화를 남겼다(KP-09).
 */
@Service
public class CoachChatService {
    private final CoachMessageRepository messages;
    private final FamilyAccess familyAccess;
    private final AiGateway gateway;
    private final TransactionTemplate tx;
    private final AppTime time;

    public CoachChatService(
            CoachMessageRepository messages,
            FamilyAccess familyAccess,
            AiGateway gateway,
            TransactionTemplate tx,
            AppTime time) {
        this.messages = messages;
        this.familyAccess = familyAccess;
        this.gateway = gateway;
        this.tx = tx;
        this.time = time;
    }

    public ChatView chat(UUID userId, ChatCommand command) {
        ProfileSummary profile = familyAccess.requireActingAs(userId, command.profileId());
        UUID conversationId = command.conversationId();
        if (conversationId == null) {
            conversationId = UUID.randomUUID();
        } else {
            UUID owner = messages.ownerOfConversation(conversationId);
            if (owner == null) throw new ConversationNotFoundException(conversationId);
            if (!owner.equals(command.profileId())) throw new CoachingForbiddenException("다른 프로필의 대화입니다");
        }

        CoachMessageResponse response = gateway.ask(new CoachMessageRequest(
                ProfileRef.of(command.profileId()), profile.ageGroup().getLabel(), command.question()));

        Instant now = time.now();
        CoachMessage user =
                CoachMessage.user(UUID.randomUUID(), conversationId, command.profileId(), command.question(), now);
        CoachMessage assistant = CoachMessage.assistant(
                UUID.randomUUID(),
                conversationId,
                command.profileId(),
                response.answer(),
                response.citations().stream()
                        .map(it -> new MessageCitation(it.index(), it.label(), it.chunkId(), it.label(), it.url()))
                        .toList(),
                response.refused(),
                response.refusalReason(),
                now.plusMillis(1));
        tx.executeWithoutResult(status -> {
            messages.save(user);
            messages.save(assistant);
        });
        return new ChatView(
                conversationId,
                assistant.getId(),
                assistant.getContent(),
                assistant.getCitations().stream()
                        .map(it -> new ChatCitationView(it.index(), it.sourceLabel(), it.excerpt(), it.url()))
                        .toList(),
                assistant.isRefused(),
                assistant.getRefusalReason());
    }
}
