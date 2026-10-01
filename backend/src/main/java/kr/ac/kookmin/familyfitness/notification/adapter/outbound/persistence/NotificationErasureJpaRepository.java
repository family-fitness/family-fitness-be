package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 탈퇴와 구성원 내보내기의 삭제 쿼리. */
public interface NotificationErasureJpaRepository extends Repository<NotificationEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from notifications
            where profile_id in (:profileIds) or about_profile_id in (:profileIds) or from_profile_id in (:profileIds)
            """, nativeQuery = true)
    int deleteOfProfiles(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from notifications where cheer_id in (:cheerIds)", nativeQuery = true)
    int deleteOfCheers(Collection<UUID> cheerIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from notifications where mission_id in (:missionIds)", nativeQuery = true)
    int deleteOfMissions(Collection<UUID> missionIds);
}
