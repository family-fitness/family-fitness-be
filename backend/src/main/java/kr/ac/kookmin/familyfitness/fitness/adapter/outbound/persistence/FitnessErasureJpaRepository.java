package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 탈퇴와 구성원 내보내기의 삭제 쿼리. 항목이 회차를 가리키므로 항목을 먼저 지운다. */
public interface FitnessErasureJpaRepository extends Repository<FitnessTestEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from fitness_test_items
            where fitness_test_id in (select t.id from fitness_tests t where t.profile_id in (:profileIds))
            """, nativeQuery = true)
    int deleteItems(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from fitness_tests where profile_id in (:profileIds)", nativeQuery = true)
    int deleteTests(Collection<UUID> profileIds);
}
