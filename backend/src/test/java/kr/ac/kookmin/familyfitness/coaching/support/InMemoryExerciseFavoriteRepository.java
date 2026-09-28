package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseFavoriteRepository;

/** 시험용 구간 찜 저장소. (프로필, 클립) → 처음 찜한 시각. */
public class InMemoryExerciseFavoriteRepository implements ExerciseFavoriteRepository {
    public record Key(UUID profileId, String clipId) {}

    public final Map<Key, Instant> rows = new LinkedHashMap<>();

    @Override
    public Set<String> clipIdsOf(UUID profileId) {
        return rows.keySet().stream()
                .filter(it -> it.profileId().equals(profileId))
                .map(Key::clipId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void add(UUID profileId, String clipId, Instant at) {
        rows.putIfAbsent(new Key(profileId, clipId), at);
    }

    @Override
    public void remove(UUID profileId, String clipId) {
        rows.remove(new Key(profileId, clipId));
    }
}
