package kr.ac.kookmin.familyfitness.progress.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.application.port.AchievementStore;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;

/** 받은 업적의 가짜 — 처음 받은 시각만 남긴다. */
class InMemoryAchievementStore implements AchievementStore {
    final Map<UUID, Map<Achievement, Instant>> earned = new HashMap<>();

    @Override
    public Map<Achievement, Instant> earnedOf(UUID profileId) {
        return Map.copyOf(earned.getOrDefault(profileId, Map.of()));
    }

    @Override
    public boolean grant(UUID profileId, Achievement achievement, Instant earnedAt) {
        return earned.computeIfAbsent(profileId, k -> new EnumMap<>(Achievement.class))
                        .putIfAbsent(achievement, earnedAt)
                == null;
    }
}
