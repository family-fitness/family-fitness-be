package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 칸 끝 기록 읽기 · 넣기. 고치거나 지우는 쿼리를 두지 않는다. */
public interface MissionSessionCompletionJpaRepository
        extends JpaRepository<MissionSessionCompletionEntity, MissionSessionCompletionId> {
    List<MissionSessionCompletionEntity> findByIdMissionId(UUID missionId);

    List<MissionSessionCompletionEntity> findByIdMissionIdIn(Collection<UUID> missionIds);

    /** 한 사람이 이 미션들에서 끝낸 칸 — 잡힌 날(여러 날짜리 미션이 서는 날) 셈에 쓴다. */
    List<MissionSessionCompletionEntity> findByIdProfileIdAndIdMissionIdIn(UUID profileId, Collection<UUID> missionIds);

    /** 여러 사람을 한 번에 — 리그 달성률의 잡힌 날 셈에 쓴다. */
    List<MissionSessionCompletionEntity> findByIdProfileIdInAndIdMissionIdIn(
            Collection<UUID> profileIds, Collection<UUID> missionIds);
}
