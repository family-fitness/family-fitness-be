package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.BodyMeasures;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class FitnessTestRepositoryAdapter implements FitnessTestRepository {
    private final FitnessTestJpaRepository jpa;

    public FitnessTestRepositoryAdapter(FitnessTestJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public FitnessTest save(FitnessTest test) {
        jpa.save(toEntity(test));
        return test;
    }

    @Override
    public @Nullable FitnessTest findById(UUID id) {
        return jpa.findById(id).map(FitnessTestRepositoryAdapter::toDomain).orElse(null);
    }

    @Override
    public @Nullable FitnessTest findLatestByProfileId(UUID profileId) {
        FitnessTestEntity entity = jpa.findFirstByProfileIdOrderByTestedOnDesc(profileId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public @Nullable FitnessTest findEarliestByProfileId(UUID profileId) {
        FitnessTestEntity entity = jpa.findFirstByProfileIdOrderByTestedOnAsc(profileId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<FitnessTest> findRecentByProfileId(UUID profileId, int limit) {
        return jpa.findByProfileIdOrderByTestedOnDesc(profileId, Limit.of(limit)).stream()
                .map(FitnessTestRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByProfileIdAndTestedOn(UUID profileId, LocalDate testedOn) {
        return jpa.existsByProfileIdAndTestedOn(profileId, testedOn);
    }

    @Override
    public boolean existsByProfileIdIn(Collection<UUID> profileIds) {
        return jpa.existsByProfileIdIn(profileIds);
    }

    @Override
    public Map<UUID, LocalDate> lastTestedOn(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) return Map.of();
        return jpa.findLastTestedOn(profileIds).stream()
                .collect(Collectors.toUnmodifiableMap(LastTestedRow::profileId, LastTestedRow::testedOn));
    }

    private static FitnessTestEntity toEntity(FitnessTest test) {
        return new FitnessTestEntity(
                test.getId(),
                test.getProfileId(),
                test.getTestedOn(),
                test.getSource().name(),
                test.getAgeAtTest(),
                test.getHeightCm(),
                test.getWeightKg(),
                test.getBodyFatPct(),
                test.getWaistCm(),
                test.getCreatedAt(),
                test.getItems().stream()
                        .map(it -> {
                            Band band = it.score().band();
                            return new FitnessTestItemEmbeddable(
                                    it.item().getCode(),
                                    it.value(),
                                    it.score().percentile(),
                                    band == null ? null : band.getWire());
                        })
                        .toList());
    }

    private static FitnessTest toDomain(FitnessTestEntity entity) {
        return FitnessTest.reconstitute(
                entity.getId(),
                entity.getProfileId(),
                entity.getTestedOn(),
                FitnessTestSource.valueOf(entity.getSource()),
                entity.getAgeAtTest(),
                new BodyMeasures(
                        entity.getHeightCm(), entity.getWeightKg(), entity.getBodyFatPct(), entity.getWaistCm()),
                entity.getItems().stream()
                        .map(it -> new FitnessTest.StoredItem(it.getItemCode(), it.getRawValue(), it.getPercentile()))
                        .toList(),
                entity.getCreatedAt());
    }
}
