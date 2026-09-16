package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class VideoInteractionPersistenceAdapter implements VideoInteractionRepository {
    private final VideoInteractionJpaRepository interactions;

    public VideoInteractionPersistenceAdapter(VideoInteractionJpaRepository interactions) {
        this.interactions = interactions;
    }

    @Override
    public @Nullable VideoInteraction find(UUID profileId, String videoId) {
        VideoInteractionEntity entity = interactions.findByProfileIdAndVideoId(profileId, videoId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public VideoInteraction save(VideoInteraction interaction) {
        VideoInteractionEntity existing =
                interactions.findById(interaction.getId()).orElse(null);
        VideoInteractionEntity entity;
        if (existing == null) {
            entity = VideoInteractionEntity.from(interaction);
        } else {
            existing.applyFrom(interaction);
            entity = existing;
        }
        interactions.save(entity);
        return interaction;
    }

    @Override
    public List<VideoInteraction> findAllOf(UUID profileId) {
        return interactions.findByProfileId(profileId).stream()
                .map(VideoInteractionEntity::toDomain)
                .toList();
    }
}
