package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FitnessTestJpaRepository extends JpaRepository<FitnessTestEntity, UUID> {
    @Nullable
    FitnessTestEntity findFirstByProfileIdOrderByTestedOnDesc(UUID profileId);

    @Nullable
    FitnessTestEntity findFirstByProfileIdOrderByTestedOnAsc(UUID profileId);

    List<FitnessTestEntity> findByProfileIdOrderByTestedOnDesc(UUID profileId, Limit limit);

    boolean existsByProfileIdAndTestedOn(UUID profileId, LocalDate testedOn);

    boolean existsByProfileIdIn(Collection<UUID> profileIds);
}
