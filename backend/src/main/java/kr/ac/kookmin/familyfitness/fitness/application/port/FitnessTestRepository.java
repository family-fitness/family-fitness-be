package kr.ac.kookmin.familyfitness.fitness.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /** testedOn 이 가장 이른 회차. */
    @Nullable
    FitnessTest findEarliestByProfileId(UUID profileId);

    /** testedOn 이 늦은 회차부터 최대 limit 개. 없으면 빈 목록. */
    List<FitnessTest> findRecentByProfileId(UUID profileId, int limit);

    boolean existsByProfileIdAndTestedOn(UUID profileId, LocalDate testedOn);

    boolean existsByProfileIdIn(Collection<UUID> profileIds);

    /** 프로필마다 가장 늦은 testedOn. 회차가 없는 프로필은 결과에 없다. */
    Map<UUID, LocalDate> lastTestedOn(Collection<UUID> profileIds);
}
