package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class ExerciseClipPersistenceAdapter implements ExerciseClipRepository {
    private final ExerciseClipJpaRepository clips;

    public ExerciseClipPersistenceAdapter(ExerciseClipJpaRepository clips) {
        this.clips = clips;
    }

    @Override
    public List<ExerciseClip> findAllActive() {
        return clips.findAllByActiveTrueOrderByVideoIdAscStartSecAsc().stream()
                .map(ExerciseClipEntity::toDomain)
                .toList();
    }

    @Override
    public @Nullable ExerciseClip findById(String clipId) {
        return clips.findById(clipId).map(ExerciseClipEntity::toDomain).orElse(null);
    }

    @Override
    public List<ExerciseClip> findAllByIds(Collection<String> clipIds) {
        if (clipIds.isEmpty()) return List.of();
        return clips.findAllById(clipIds).stream()
                .map(ExerciseClipEntity::toDomain)
                .toList();
    }
}
