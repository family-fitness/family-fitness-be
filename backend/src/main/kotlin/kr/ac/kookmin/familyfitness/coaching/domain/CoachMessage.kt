package kr.ac.kookmin.familyfitness.coaching.domain

import java.time.Instant
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
}

/** 답변의 근거. [index] 는 AI 가 붙인 1부터 시작하는 번호이며 답변 본문의 `[n]` 과 맞물린다. */
data class MessageCitation(
    val index: Int,
    val sourceLabel: String,
    val chunkId: String?,
    val excerpt: String,
    val url: String?,
)

/**
 * 코치 대화 메시지. 한 대화(conversationId)는 한 프로필의 것이다.
 * 거부(refused)도 오류가 아니라 메시지로 저장한다.
 */
class CoachMessage private constructor(
    val id: UUID,
    val conversationId: UUID,
    val profileId: UUID,
    val role: MessageRole,
    val content: String,
    val refused: Boolean,
    val refusalReason: String?,
    val citations: List<MessageCitation>,
    val createdAt: Instant,
) {
    companion object {
        /** refused=false 인데 인용이 0개인 답변은 근거 없는 답이므로 서버가 거부로 바꾼다. */
        const val NO_CITATION_GENERATED = "no_citation_generated"

        fun user(
            id: UUID,
            conversationId: UUID,
            profileId: UUID,
            question: String,
            at: Instant,
        ): CoachMessage = CoachMessage(id, conversationId, profileId, MessageRole.USER, question, false, null, emptyList(), at)

        fun assistant(
            id: UUID,
            conversationId: UUID,
            profileId: UUID,
            answer: String,
            citations: List<MessageCitation>,
            refused: Boolean,
            refusalReason: String?,
            at: Instant,
        ): CoachMessage {
            val uncited = !refused && citations.isEmpty()
            return CoachMessage(
                id = id,
                conversationId = conversationId,
                profileId = profileId,
                role = MessageRole.ASSISTANT,
                content = answer,
                refused = refused || uncited,
                refusalReason = if (uncited) NO_CITATION_GENERATED else refusalReason,
                citations = if (refused) emptyList() else citations,
                createdAt = at,
            )
        }

        fun reconstitute(
            id: UUID,
            conversationId: UUID,
            profileId: UUID,
            role: MessageRole,
            content: String,
            refused: Boolean,
            refusalReason: String?,
            citations: List<MessageCitation>,
            createdAt: Instant,
        ): CoachMessage = CoachMessage(id, conversationId, profileId, role, content, refused, refusalReason, citations, createdAt)
    }
}
