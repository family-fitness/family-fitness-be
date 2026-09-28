package kr.ac.kookmin.familyfitness.fitness.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import org.jspecify.annotations.Nullable;

class InMemoryFitnessTestRepository implements FitnessTestRepository {
    final Map<UUID, FitnessTest> saved = new LinkedHashMap<>();

    @Override
    public FitnessTest save(FitnessTest test) {
        saved.put(test.getId(), test);
        return test;
    }

    @Override
    public @Nullable FitnessTest findById(UUID id) {
        return saved.get(id);
    }

    @Override
    public @Nullable FitnessTest findLatestByProfileId(UUID profileId) {
        return saved.values().stream()
                .filter(it -> it.getProfileId().equals(profileId))
                .max(Comparator.comparing(FitnessTest::getTestedOn))
                .orElse(null);
    }

    @Override
    public List<FitnessTest> findRecentByProfileId(UUID profileId, int limit) {
        return saved.values().stream()
                .filter(it -> it.getProfileId().equals(profileId))
                .sorted(Comparator.comparing(FitnessTest::getTestedOn).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public boolean existsByProfileIdAndTestedOn(UUID profileId, LocalDate testedOn) {
        return saved.values().stream()
                .anyMatch(it ->
                        it.getProfileId().equals(profileId) && it.getTestedOn().equals(testedOn));
    }

    @Override
    public boolean existsByProfileIdIn(Collection<UUID> profileIds) {
        return saved.values().stream().anyMatch(it -> profileIds.contains(it.getProfileId()));
    }
}
