package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MissionSessionJpaRepository extends JpaRepository<MissionSessionEntity, MissionSessionId> {
    List<MissionSessionEntity> findByIdMissionId(UUID missionId);

    List<MissionSessionEntity> findByIdMissionIdIn(Collection<UUID> missionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MissionSessionEntity s where s.id.missionId = :missionId")
    int deleteByMission(UUID missionId);
}
