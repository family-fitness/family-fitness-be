package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MissionParticipantJpaRepository extends JpaRepository<MissionParticipantEntity, MissionParticipantId> {
    List<MissionParticipantEntity> findByIdMissionId(UUID missionId);

    List<MissionParticipantEntity> findByIdMissionIdIn(Collection<UUID> missionIds);

    /** 이 프로필의 참여 행과 그 미션의 기간 · 지표. 칸 · 다른 참여자는 읽지 않는다. */
    @Query("""
            select new kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence.MissionSpanRow(
                m.startsOn, m.endsOn, m.targetMetric, p.status, p.progress, p.verifiedAt)
            from MissionParticipantEntity p, MissionEntity m
            where m.id = p.id.missionId and p.id.profileId = :profileId
              and m.startsOn <= :to and m.endsOn >= :from
            """)
    List<MissionSpanRow> findSpans(UUID profileId, LocalDate from, LocalDate to);
}
