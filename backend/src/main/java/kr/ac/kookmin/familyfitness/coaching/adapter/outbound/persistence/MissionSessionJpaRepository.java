package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionSessionJpaRepository extends JpaRepository<MissionSessionEntity, MissionSessionId> {
    List<MissionSessionEntity> findByIdMissionId(UUID missionId);

    List<MissionSessionEntity> findByIdMissionIdIn(Collection<UUID> missionIds);
}
