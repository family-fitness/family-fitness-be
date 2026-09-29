package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class ExerciseVideoPersistenceAdapter implements ExerciseVideoRepository {
    private final ExerciseVideoJpaRepository videos;

    public ExerciseVideoPersistenceAdapter(ExerciseVideoJpaRepository videos) {
        this.videos = videos;
    }

    @Override
    public @Nullable ExerciseVideo findById(String videoId) {
        return videos.findById(videoId).map(ExerciseVideoEntity::toDomain).orElse(null);
    }

    @Override
    public List<ExerciseVideo> findAllByIds(Collection<String> videoIds) {
        if (videoIds.isEmpty()) return List.of();
        return videos.findAllById(videoIds).stream()
                .map(ExerciseVideoEntity::toDomain)
                .toList();
    }

    @Override
    public List<ExerciseVideo> findAllAfter(@Nullable String afterVideoId) {
        List<ExerciseVideoEntity> entities =
                afterVideoId == null ? videos.findListed() : videos.findListedAfter(afterVideoId);
        return entities.stream().map(ExerciseVideoEntity::toDomain).toList();
    }
}
