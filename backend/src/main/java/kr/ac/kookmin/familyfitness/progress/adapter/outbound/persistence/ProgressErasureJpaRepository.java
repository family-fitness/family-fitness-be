package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 탈퇴와 구성원 내보내기의 삭제 쿼리. 이때만 원장 행을 지우거나 고친다. */
public interface ProgressErasureJpaRepository extends Repository<XpEventEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from progress_xp_events where profile_id in (:profileIds)", nativeQuery = true)
    int deleteXp(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from progress_achievements where profile_id in (:profileIds)", nativeQuery = true)
    int deleteAchievements(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update progress_xp_events set from_profile_id = null where from_profile_id in (:profileIds)",
            nativeQuery = true)
    int forgetSenders(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update progress_xp_events set mission_id = null where mission_id in (:missionIds)",
            nativeQuery = true)
    int forgetMissions(Collection<UUID> missionIds);
}
