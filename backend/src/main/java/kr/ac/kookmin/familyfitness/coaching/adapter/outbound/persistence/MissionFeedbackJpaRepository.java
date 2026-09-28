package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MissionFeedbackJpaRepository extends JpaRepository<MissionFeedbackEntity, MissionFeedbackId> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update mission_feedback set feel = :feel, created_at = :createdAt
            where mission_id = :missionId and profile_id = :profileId
            """, nativeQuery = true)
    int updateFeel(UUID missionId, UUID profileId, String feel, Instant createdAt);

    /** 이미 있으면 아무것도 하지 않는다. ON CONFLICT DO NOTHING 은 PostgreSQL 과 H2(MODE=PostgreSQL) 둘 다 받는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into mission_feedback (mission_id, profile_id, feel, created_at)
            values (:missionId, :profileId, :feel, :createdAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID missionId, UUID profileId, String feel, Instant createdAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MissionFeedbackEntity f where f.id.missionId = :missionId")
    int deleteByMission(UUID missionId);
}
