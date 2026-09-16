package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction;
import org.jspecify.annotations.Nullable;

public interface VideoInteractionRepository {
    @Nullable
    VideoInteraction find(UUID profileId, String videoId);

    VideoInteraction save(VideoInteraction interaction);

    List<VideoInteraction> findAllOf(UUID profileId);
}
