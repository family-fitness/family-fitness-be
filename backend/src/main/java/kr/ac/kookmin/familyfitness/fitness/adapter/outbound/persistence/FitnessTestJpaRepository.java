package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FitnessTestJpaRepository extends JpaRepository<FitnessTestEntity, UUID> {
    @Nullable
    FitnessTestEntity findFirstByProfileIdOrderByTestedOnDesc(UUID profileId);

    @Nullable
    FitnessTestEntity findFirstByProfileIdOrderByTestedOnAsc(UUID profileId);

    List<FitnessTestEntity> findByProfileIdOrderByTestedOnDesc(UUID profileId, Limit limit);

    /**
     * 프로필마다 가장 늦은 회차. (profile_id, tested_on) 이 유니크(uq_fitness_test_date)라 프로필마다 한 행이다.
     * 항목(fitness_test_items)은 엔티티의 BatchSize 로 회차 여럿을 한 번에 읽는다.
     */
    @Query("""
            select t from FitnessTestEntity t
            where t.profileId in :profileIds
              and t.testedOn = (select max(t2.testedOn) from FitnessTestEntity t2 where t2.profileId = t.profileId)
            """)
    List<FitnessTestEntity> findLatestOf(Collection<UUID> profileIds);

    boolean existsByProfileIdAndTestedOn(UUID profileId, LocalDate testedOn);

    boolean existsByProfileIdIn(Collection<UUID> profileIds);

    /** 프로필마다 가장 늦은 tested_on. uq_fitness_test_date(profile_id, tested_on) 인덱스를 탄다. */
    @Query("""
            select new kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence.LastTestedRow(
                t.profileId, max(t.testedOn))
            from FitnessTestEntity t
            where t.profileId in :profileIds
            group by t.profileId
            """)
    List<LastTestedRow> findLastTestedOn(Collection<UUID> profileIds);
}
