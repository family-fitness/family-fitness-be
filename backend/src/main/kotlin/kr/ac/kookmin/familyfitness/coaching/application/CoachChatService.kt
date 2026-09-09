package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.application.port.CoachMessageRepository
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage
import kr.ac.kookmin.familyfitness.coaching.domain.CoachingForbiddenException
import kr.ac.kookmin.familyfitness.coaching.domain.ConversationNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.MessageCitation
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageRequest
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class ChatCommand(
    val profileId: UUID,
    val conversationId: UUID?,
    val question: String,
)

data class ChatView(
    val conversationId: UUID,
    val messageId: UUID,
    val answer: String,
    val citations: List<ChatCitationView>,
    val refused: Boolean,
    val refusalReason: String?,
)

data class ChatCitationView(
    val index: Int,
    val sourceLabel: String,
    val excerpt: String,
    val url: String?,
)

/**
 * 코치 대화. USER·ASSISTANT 메시지를 모두 저장한다(거부도 저장).
 * AI 호출은 트랜잭션 밖에서 하고, 성공했을 때만 두 메시지를 한 트랜잭션으로 저장한다 — AI 장애(503)면 아무것도 남지 않는다.
 */
@Service
class CoachChatService(
    private val messages: CoachMessageRepository,
    private val familyAccess: FamilyAccess,
    private val gateway: AiGateway,
    private val tx: TransactionTemplate,
    private val time: AppTime,
) {
    fun chat(
        userId: UUID,
        command: ChatCommand,
    ): ChatView {
        val profile = familyAccess.requireSameFamilyAsProfile(userId, command.profileId)
        val conversationId =
            command.conversationId?.also { id ->
                val owner = messages.ownerOfConversation(id) ?: throw ConversationNotFoundException(id)
                if (owner != command.profileId) throw CoachingForbiddenException("다른 프로필의 대화입니다")
            } ?: UUID.randomUUID()

        val response =
            gateway.ask(CoachMessageRequest(ProfileRef.of(command.profileId), profile.ageGroup.label, command.question))

        val now = time.now()
        val user = CoachMessage.user(UUID.randomUUID(), conversationId, command.profileId, command.question, now)
        val assistant =
            CoachMessage.assistant(
                id = UUID.randomUUID(),
                conversationId = conversationId,
                profileId = command.profileId,
                answer = response.answer,
                citations = response.citations.map { MessageCitation(it.index, it.label, it.chunkId, it.label, it.url) },
                refused = response.refused,
                refusalReason = response.refusalReason,
                at = now.plusMillis(1),
            )
        tx.executeWithoutResult {
            messages.save(user)
            messages.save(assistant)
        }
        return ChatView(
            conversationId = conversationId,
            messageId = assistant.id,
            answer = assistant.content,
            citations = assistant.citations.map { ChatCitationView(it.index, it.sourceLabel, it.excerpt, it.url) },
            refused = assistant.refused,
            refusalReason = assistant.refusalReason,
        )
    }
}
