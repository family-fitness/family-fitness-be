package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilitySlotJpaRepository extends JpaRepository<AvailabilitySlotEntity, AvailabilitySlotId> {
    List<AvailabilitySlotEntity> findByIdProfileId(UUID profileId);

    /** 한 주를 다시 쓰기 전에 그 프로필의 칸을 한 문장으로 지운다. 같은 키로 새 행을 넣으므로 영속성 컨텍스트도 비운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AvailabilitySlotEntity s where s.id.profileId = :profileId")
    int deleteByProfileId(@Param("profileId") UUID profileId);

    /** 같은 프로필의 한 주 바꾸기를 차례대로 하려고 프로필 행을 잠근다. 트랜잭션이 끝날 때 풀린다. */
    @Query(value = "select 1 from profiles where id = :profileId for update", nativeQuery = true)
    List<Integer> lockProfile(@Param("profileId") UUID profileId);
}
