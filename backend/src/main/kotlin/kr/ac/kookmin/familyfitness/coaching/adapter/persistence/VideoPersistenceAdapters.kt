package kr.ac.kookmin.familyfitness.coaching.adapter.persistence

import kr.ac.kookmin.familyfitness.coaching.application.port.CoachMessageRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ExerciseVideoPersistenceAdapter(
    private val videos: ExerciseVideoJpaRepository,
) : ExerciseVideoRepository {
    override fun findById(videoId: String): ExerciseVideo? = videos.findById(videoId).orElse(null)?.toDomain()

    override fun findAllByIds(videoIds: Collection<String>): List<ExerciseVideo> =
        if (videoIds.isEmpty()) emptyList() else videos.findAllById(videoIds).map { it.toDomain() }

    override fun findAllAfter(afterVideoId: String?): List<ExerciseVideo> =
        (afterVideoId?.let { videos.findByVideoIdGreaterThanOrderByVideoIdAsc(it) } ?: videos.findAllByOrderByVideoIdAsc())
            .map { it.toDomain() }
}

@Repository
class VideoInteractionPersistenceAdapter(
    private val interactions: VideoInteractionJpaRepository,
) : VideoInteractionRepository {
    override fun find(
        profileId: UUID,
        videoId: String,
    ): VideoInteraction? = interactions.findByProfileIdAndVideoId(profileId, videoId)?.toDomain()

    override fun save(interaction: VideoInteraction): VideoInteraction {
        val entity =
            interactions.findById(interaction.id).orElse(null)?.also { it.applyFrom(interaction) }
                ?: VideoInteractionEntity.from(interaction)
        interactions.save(entity)
        return interaction
    }

    override fun findAllOf(profileId: UUID): List<VideoInteraction> = interactions.findByProfileId(profileId).map { it.toDomain() }
}

@Repository
class CoachMessagePersistenceAdapter(
    private val messages: CoachMessageJpaRepository,
    private val citations: CoachMessageCitationJpaRepository,
) : CoachMessageRepository {
    override fun save(message: CoachMessage): CoachMessage {
        messages.save(
            CoachMessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                profileId = message.profileId,
                role = message.role.name,
                content = message.content,
                refused = message.refused,
                refusalReason = message.refusalReason,
                createdAt = message.createdAt,
            ),
        )
        citations.saveAll(
            message.citations.map {
                CoachMessageCitationEntity(
                    id = CoachMessageCitationId(message.id, it.index),
                    chunkId = it.chunkId?.take(200),
                    sourceLabel = it.sourceLabel.take(200),
                    excerpt = it.excerpt.take(500),
                    url = it.url?.take(500),
                )
            },
        )
        return message
    }

    override fun ownerOfConversation(conversationId: UUID): UUID? =
        messages.findFirstByConversationIdOrderByCreatedAtAsc(conversationId)?.profileId
}
