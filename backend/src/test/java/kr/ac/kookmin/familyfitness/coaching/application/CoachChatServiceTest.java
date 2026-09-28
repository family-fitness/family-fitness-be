package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachingForbiddenException;
import kr.ac.kookmin.familyfitness.coaching.domain.ConversationNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MessageRole;
import kr.ac.kookmin.familyfitness.coaching.support.FakeAiGateway;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachMessageRepository;
import kr.ac.kookmin.familyfitness.coaching.support.NoopTransactionManager;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException;
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageResponse;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoachChatServiceTest {
    private final Family family = new Family();
    private final Family other = new Family();
    private final FakeIdentity identity = new FakeIdentity(family, other);
    private final InMemoryCoachMessageRepository messages = new InMemoryCoachMessageRepository();
    private final FakeAiGateway gateway = new FakeAiGateway();
    private final CoachChatService service = new CoachChatService(
            messages, identity, gateway, NoopTransactionManager.noopTransactionTemplate(), Fixed.time());
    private final UUID childId = family.child.profileId();

    @Test
    @DisplayName("질문과 답을 한 대화로 저장하고 인용을 돌려준다")
    void 질문과_답을_한_대화로_저장하고_인용을_돌려준다() {
        ChatView view = service.chat(family.childUser, new ChatCommand(childId, null, "유연성 운동 뭐가 좋아요?"));

        assertThat(view.refused()).isFalse();
        assertThat(view.answer()).contains("[1]");
        assertThat(view.citations()).hasSize(1);
        assertThat(view.citations().getFirst().index()).isEqualTo(1);
        assertThat(view.citations().getFirst().excerpt())
                .isEqualTo(view.citations().getFirst().sourceLabel());
        assertThat(messages.messages.stream().map(CoachMessage::getRole).toList())
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(messages.messages.stream()
                        .allMatch(it -> it.getConversationId().equals(view.conversationId())))
                .isTrue();
        assertThat(messages.messages.stream().allMatch(it -> it.getProfileId().equals(childId)))
                .isTrue();
        assertThat(messages.messages.getLast().getId()).isEqualTo(view.messageId());

        ChatView followUp = service.chat(family.childUser, new ChatCommand(childId, view.conversationId(), "더 알려줘"));
        assertThat(followUp.conversationId()).isEqualTo(view.conversationId());
        assertThat(messages.messages).hasSize(4);
    }

    @Test
    @DisplayName("AI 요청에는 프로필 ref 와 연령대 라벨만 실린다")
    void AI_요청에는_프로필_ref_와_연령대_라벨만_실린다() {
        AtomicReference<CoachMessageRequest> captured = new AtomicReference<>();
        gateway.onAsk = request -> {
            captured.set(request);
            return new CoachMessageResponse("", List.of(), true, "no_relevant_source");
        };

        service.chat(family.childUser, new ChatCommand(childId, null, "질문"));

        assertThat(captured.get().profileRef()).isEqualTo(ProfileRef.of(childId));
        assertThat(captured.get().ageGroup()).isEqualTo("유소년");
        assertThat(captured.get().question()).isEqualTo("질문");
    }

    @Test
    @DisplayName("거부도 200 이며 저장된다")
    void 거부도_200_이며_저장된다() {
        ChatView view = service.chat(family.childUser, new ChatCommand(childId, null, "무릎 통증이 있는데 운동해도 돼요?"));

        assertThat(view.refused()).isTrue();
        assertThat(view.refusalReason()).isEqualTo("medical_query");
        assertThat(view.citations()).isEmpty();
        assertThat(messages.messages.getLast().isRefused()).isTrue();
        assertThat(messages.messages.getLast().getRefusalReason()).isEqualTo("medical_query");
    }

    @Test
    @DisplayName("인용 없는 답은 no_citation_generated 거부로 바뀌어 저장된다")
    void 인용_없는_답은_no_citation_generated_거부로_바뀌어_저장된다() {
        gateway.onAsk = request -> new CoachMessageResponse("근거 없는 답", List.of(), false, null);

        ChatView view = service.chat(family.childUser, new ChatCommand(childId, null, "질문"));

        assertThat(view.refused()).isTrue();
        assertThat(view.refusalReason()).isEqualTo("no_citation_generated");
        assertThat(messages.messages.getLast().getRefusalReason()).isEqualTo("no_citation_generated");
    }

    @Test
    @DisplayName("AI 장애면 503 이고 아무것도 저장하지 않는다")
    void AI_장애면_503_이고_아무것도_저장하지_않는다() {
        gateway.onAsk = request -> {
            throw new AiUnavailableException("timeout");
        };

        AiUnavailableException e = assertThrows(
                AiUnavailableException.class,
                () -> service.chat(family.childUser, new ChatCommand(childId, null, "질문")));

        assertThat(e.getCode()).isEqualTo("TEMPORARILY_UNAVAILABLE");
        assertThat(messages.messages).isEmpty();
    }

    @Test
    @DisplayName("다른 프로필의 대화는 FORBIDDEN, 없는 대화는 CONVERSATION_NOT_FOUND, 다른 가족은 NOT_SAME_FAMILY")
    void 다른_프로필의_대화는_FORBIDDEN_없는_대화는_CONVERSATION_NOT_FOUND_다른_가족은_NOT_SAME_FAMILY() {
        UUID conversation = service.chat(family.childUser, new ChatCommand(childId, null, "질문"))
                .conversationId();

        CoachingForbiddenException forbidden = assertThrows(
                CoachingForbiddenException.class,
                () -> service.chat(family.parentUser, new ChatCommand(family.parent.profileId(), conversation, "질문")));
        assertThat(forbidden.getCode()).isEqualTo("FORBIDDEN");
        assertThat(assertThrows(
                                ConversationNotFoundException.class,
                                () -> service.chat(family.childUser, new ChatCommand(childId, UUID.randomUUID(), "질문")))
                        .getCode())
                .isEqualTo("CONVERSATION_NOT_FOUND");
        assertThrows(
                NotSameFamilyException.class,
                () -> service.chat(other.parentUser, new ChatCommand(childId, null, "질문")));
        assertThat(messages.messages).hasSize(2);
    }

    @Test
    @DisplayName("대화는 이 계정이 그 프로필 이름으로 할 수 있을 때만 — 자녀 계정이 부모 이름으로, 보호자가 계정 있는 아이 이름으로 물으면 403 이고 저장하지 않는다")
    void 대화는_그_프로필_이름으로_할_수_있을_때만() {
        assertThrows(
                CannotActAsProfileException.class,
                () -> service.chat(family.childUser, new ChatCommand(family.parent.profileId(), null, "질문")));
        assertThrows(
                CannotActAsProfileException.class,
                () -> service.chat(family.parentUser, new ChatCommand(childId, null, "질문")));
        assertThat(messages.messages).isEmpty();

        UUID accountless = family.addChild("하늘", LocalDate.of(2017, 4, 2)).profileId();
        ChatView forAccountless = service.chat(family.parentUser, new ChatCommand(accountless, null, "질문"));
        assertThat(forAccountless.refused()).isFalse();
        assertThat(messages.messages).allMatch(it -> it.getProfileId().equals(accountless));
    }
}
