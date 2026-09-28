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

    /** {@link #findSpans} 를 여러 프로필에 한 번에. 미션을 만든 시각을 같이 읽는다(리그 달성률의 잡힌 날). */
    @Query("""
            select new kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence.ParticipantSpanRow(
                p.id.profileId, m.startsOn, m.endsOn, m.targetMetric, p.status, p.progress, p.verifiedAt, m.createdAt)
            from MissionParticipantEntity p, MissionEntity m
            where m.id = p.id.missionId and p.id.profileId in :profileIds
              and m.startsOn <= :to and m.endsOn >= :from
            """)
    List<ParticipantSpanRow> findParticipantSpans(Collection<UUID> profileIds, LocalDate from, LocalDate to);
}
