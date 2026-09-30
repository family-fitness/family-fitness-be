package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction;
import org.jspecify.annotations.Nullable;

public class InMemoryVideoInteractionRepository implements VideoInteractionRepository {
    public final ConcurrentHashMap<UUID, VideoInteraction> interactions = new ConcurrentHashMap<>();

    @Override
    public @Nullable VideoInteraction find(UUID profileId, String videoId) {
        return interactions.values().stream()
                .filter(it ->
                        it.getProfileId().equals(profileId) && it.getVideoId().equals(videoId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public VideoInteraction save(VideoInteraction interaction) {
        interactions.put(interaction.getId(), interaction);
        return interaction;
    }

    @Override
    public List<VideoInteraction> findAllOf(UUID profileId) {
        return interactions.values().stream()
                .filter(it -> it.getProfileId().equals(profileId))
                .toList();
    }
}
