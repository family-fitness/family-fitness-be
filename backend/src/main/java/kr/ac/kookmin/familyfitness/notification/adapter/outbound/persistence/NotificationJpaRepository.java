package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
    boolean existsByProfileIdAndDedupeKey(UUID profileId, String dedupeKey);

    /** 알림함 목록. ix_notifications_profile_created(profile_id, created_at desc)를 탄다. 같은 시각이면 id 차례로 고정한다. */
    @Query("""
            select n from NotificationEntity n
            where n.profileId = :profileId
              and (n.kind <> 'MISSION_READY' or n.eventDate >= :readyOn)
              and (n.kind <> 'ACHIEVEMENT' or n.eventDate >= :achievementsSince)
            order by n.createdAt desc, n.id desc
            """)
    List<NotificationEntity> findShown(UUID profileId, LocalDate readyOn, LocalDate achievementsSince, Limit limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationEntity n set n.readAt = :readAt where n.profileId = :profileId and n.readAt is null")
    int markAllRead(UUID profileId, Instant readAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationEntity n set n.readAt = :readAt
            where n.profileId = :profileId and n.readAt is null and n.createdAt <= :upTo
            """)
    int markReadUpTo(UUID profileId, Instant upTo, Instant readAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from NotificationEntity n
            where n.profileId = :profileId and n.missionId = :missionId and n.kind = :kind
            """)
    int deleteOfProfileMission(UUID profileId, UUID missionId, String kind);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from NotificationEntity n where n.missionId = :missionId")
    int deleteOfMission(UUID missionId);
}
