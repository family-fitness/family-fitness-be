package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoInteractionJpaRepository extends JpaRepository<VideoInteractionEntity, UUID> {
    @Nullable
    VideoInteractionEntity findByProfileIdAndVideoId(UUID profileId, String videoId);

    List<VideoInteractionEntity> findByProfileId(UUID profileId);
}
