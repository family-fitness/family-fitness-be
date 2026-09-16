package kr.ac.kookmin.familyfitness.fitness.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import org.jspecify.annotations.Nullable;

public interface FitnessTestRepository {
    FitnessTest save(FitnessTest test);

    @Nullable
    FitnessTest findById(UUID id);

    /** testedOn 이 가장 늦은 회차. */
    @Nullable
    FitnessTest findLatestByProfileId(UUID profileId);

    boolean existsByProfileIdAndTestedOn(UUID profileId, LocalDate testedOn);

    boolean existsByProfileIdIn(Collection<UUID> profileIds);
}
