package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionParticipantJpaRepository extends JpaRepository<MissionParticipantEntity, MissionParticipantId> {
    List<MissionParticipantEntity> findByIdMissionId(UUID missionId);

    List<MissionParticipantEntity> findByIdMissionIdIn(Collection<UUID> missionIds);
}
