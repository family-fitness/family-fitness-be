package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FitnessTestJpaRepository extends JpaRepository<FitnessTestEntity, UUID> {
    @Nullable
    FitnessTestEntity findFirstByProfileIdOrderByTestedOnDesc(UUID profileId);

    boolean existsByProfileIdAndTestedOn(UUID profileId, LocalDate testedOn);

    boolean existsByProfileIdIn(Collection<UUID> profileIds);
}
