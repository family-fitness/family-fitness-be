package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.domain.CoachingForbiddenException
import kr.ac.kookmin.familyfitness.coaching.domain.ConversationNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.MessageRole
import kr.ac.kookmin.familyfitness.coaching.support.FakeAiGateway
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.Fixed
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachMessageRepository
import kr.ac.kookmin.familyfitness.coaching.support.noopTransactionTemplate
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageResponse
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class CoachChatServiceTest {
    private val family = Family()
    private val other = Family()
    private val identity = FakeIdentity(family, other)
    private val messages = InMemoryCoachMessageRepository()
    private val gateway = FakeAiGateway()
    private val service = CoachChatService(messages, identity, gateway, noopTransactionTemplate(), Fixed.time())
    private val childId = family.child.profileId

    @Test
    fun `질문과 답을 한 대화로 저장하고 인용을 돌려준다`() {
        val view = service.chat(family.parentUser, ChatCommand(childId, null, "유연성 운동 뭐가 좋아요?"))

        assertThat(view.refused).isFalse()
        assertThat(view.answer).contains("[1]")
        assertThat(view.citations.single().index).isEqualTo(1)
        assertThat(view.citations.single().excerpt).isEqualTo(view.citations.single().sourceLabel)
        assertThat(messages.messages.map { it.role }).containsExactly(MessageRole.USER, MessageRole.ASSISTANT)
        assertThat(messages.messages.all { it.conversationId == view.conversationId }).isTrue()
        assertThat(messages.messages.all { it.profileId == childId }).isTrue()
        assertThat(messages.messages.last().id).isEqualTo(view.messageId)

        val followUp = service.chat(family.childUser, ChatCommand(childId, view.conversationId, "더 알려줘"))
        assertThat(followUp.conversationId).isEqualTo(view.conversationId)
        assertThat(messages.messages).hasSize(4)
    }

    @Test
    fun `AI 요청에는 프로필 ref 와 연령대 라벨만 실린다`() {
        var captured: kr.ac.kookmin.familyfitness.shared.ai.CoachMessageRequest? = null
        gateway.onAsk = {
            captured = it
            CoachMessageResponse("", emptyList(), true, "no_relevant_source")
        }

        service.chat(family.parentUser, ChatCommand(childId, null, "질문"))

        assertThat(captured!!.profileRef).isEqualTo(ProfileRef.of(childId))
        assertThat(captured!!.ageGroup).isEqualTo("유소년")
        assertThat(captured!!.question).isEqualTo("질문")
    }

    @Test
    fun `거부도 200 이며 저장된다`() {
        val view = service.chat(family.childUser, ChatCommand(childId, null, "무릎 통증이 있는데 운동해도 돼요?"))

        assertThat(view.refused).isTrue()
        assertThat(view.refusalReason).isEqualTo("medical_query")
        assertThat(view.citations).isEmpty()
        assertThat(messages.messages.last().refused).isTrue()
        assertThat(messages.messages.last().refusalReason).isEqualTo("medical_query")
    }

    @Test
    fun `인용 없는 답은 no_citation_generated 거부로 바뀌어 저장된다`() {
        gateway.onAsk = { CoachMessageResponse("근거 없는 답", emptyList(), false, null) }

        val view = service.chat(family.childUser, ChatCommand(childId, null, "질문"))

        assertThat(view.refused).isTrue()
        assertThat(view.refusalReason).isEqualTo("no_citation_generated")
        assertThat(messages.messages.last().refusalReason).isEqualTo("no_citation_generated")
    }

    @Test
    fun `AI 장애면 503 이고 아무것도 저장하지 않는다`() {
        gateway.onAsk = { throw AiUnavailableException("timeout") }

        val e = assertThrows<AiUnavailableException> { service.chat(family.childUser, ChatCommand(childId, null, "질문")) }

        assertThat(e.code).isEqualTo("TEMPORARILY_UNAVAILABLE")
        assertThat(messages.messages).isEmpty()
    }

    @Test
    fun `다른 프로필의 대화는 FORBIDDEN, 없는 대화는 CONVERSATION_NOT_FOUND, 다른 가족은 NOT_SAME_FAMILY`() {
        val conversation = service.chat(family.childUser, ChatCommand(childId, null, "질문")).conversationId

        val forbidden =
            assertThrows<CoachingForbiddenException> {
                service.chat(
                    family.parentUser,
                    ChatCommand(family.parent.profileId, conversation, "질문"),
                )
            }
        assertThat(forbidden.code).isEqualTo("FORBIDDEN")
        assertThat(
            assertThrows<ConversationNotFoundException> {
                service.chat(family.childUser, ChatCommand(childId, UUID.randomUUID(), "질문"))
            }.code,
        ).isEqualTo("CONVERSATION_NOT_FOUND")
        assertThrows<NotSameFamilyException> { service.chat(other.parentUser, ChatCommand(childId, null, "질문")) }
        assertThat(messages.messages).hasSize(2)
    }
}
