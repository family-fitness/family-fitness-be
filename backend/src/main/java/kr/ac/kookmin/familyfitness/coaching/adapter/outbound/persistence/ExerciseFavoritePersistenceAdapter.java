package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseFavoriteRepository;
import org.springframework.stereotype.Repository;

@Repository
public class ExerciseFavoritePersistenceAdapter implements ExerciseFavoriteRepository {
    private final ExerciseFavoriteJpaRepository favorites;

    public ExerciseFavoritePersistenceAdapter(ExerciseFavoriteJpaRepository favorites) {
        this.favorites = favorites;
    }

    @Override
    public Set<String> clipIdsOf(UUID profileId) {
        return Set.copyOf(favorites.findClipIdsByProfileId(profileId));
    }

    @Override
    public void add(UUID profileId, String clipId, Instant at) {
        favorites.insertIfAbsent(profileId, clipId, at);
    }

    @Override
    public void remove(UUID profileId, String clipId) {
        favorites.deleteOne(profileId, clipId);
    }
}
