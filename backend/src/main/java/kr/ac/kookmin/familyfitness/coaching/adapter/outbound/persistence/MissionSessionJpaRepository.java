package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MissionSessionJpaRepository extends JpaRepository<MissionSessionEntity, MissionSessionId> {
    List<MissionSessionEntity> findByIdMissionId(UUID missionId);

    List<MissionSessionEntity> findByIdMissionIdIn(Collection<UUID> missionIds);

    /** 이 프로필이 참여자인 미션 중 시작일이 from~to 인 것의 칸 영상 id. 최근 미션부터, 한 미션 안은 칸 차례. 같은 id 가 여러 번 나올 수 있다. */
    @Query("""
            select s.videoId
            from MissionSessionEntity s, MissionEntity m, MissionParticipantEntity p
            where s.id.missionId = m.id and p.id.missionId = m.id and p.id.profileId = :profileId
              and m.startsOn >= :from and m.startsOn <= :to and s.videoId is not null
            order by m.startsOn desc, m.createdAt desc, s.id.position asc
            """)
    List<String> findVideoIdsOf(UUID profileId, LocalDate from, LocalDate to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MissionSessionEntity s where s.id.missionId = :missionId")
    int deleteByMission(UUID missionId);
}
