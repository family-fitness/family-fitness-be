package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 탈퇴와 구성원 내보내기의 삭제 쿼리. 쿼리마다 영속성 컨텍스트를 먼저 flush 하고 끝나면 clear 한다. */
public interface ActivityErasureJpaRepository extends Repository<ActivityDailyEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from activity_daily where profile_id in (:profileIds)", nativeQuery = true)
    int deleteDaily(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update rest_cards set created_by = :heir where created_by = :profileId", nativeQuery = true)
    int handOverRestCards(UUID profileId, UUID heir);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from rest_cards where family_id = :familyId", nativeQuery = true)
    int deleteRestCards(UUID familyId);
}
