package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coach_messages")
class CoachMessageEntity(
    @Id
    @Column(name = "id")
    val id: UUID,
    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID,
    @Column(name = "profile_id", nullable = false)
    val profileId: UUID,
    @Column(name = "role", nullable = false, length = 10)
    val role: String,
    @Column(name = "content", nullable = false)
    val content: String,
    @Column(name = "refused", nullable = false)
    val refused: Boolean,
    @Column(name = "refusal_reason", length = 40)
    val refusalReason: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

@Embeddable
data class CoachMessageCitationId(
    @Column(name = "coach_message_id")
    val coachMessageId: UUID,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position")
    val position: Int,
) : Serializable

@Entity
@Table(name = "coach_message_citations")
class CoachMessageCitationEntity(
    @EmbeddedId
    val id: CoachMessageCitationId,
    @Column(name = "chunk_id", length = 200)
    val chunkId: String?,
    @Column(name = "source_label", nullable = false, length = 200)
    val sourceLabel: String,
    @Column(name = "excerpt", length = 500)
    val excerpt: String?,
    @Column(name = "url", length = 500)
    val url: String?,
)
