package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
    /**
     * 알림함 목록. ix_notifications_profile_created(profile_id, created_at desc)를 탄다. 같은 시각이면 id 차례로 고정한다.
     * {@code readyShown} 이 false 면(오늘이 쉬는 날) MISSION_READY 를 하나도 싣지 않는다.
     */
    @Query("""
            select n from NotificationEntity n
            where n.profileId = :profileId
              and (n.kind <> 'MISSION_READY' or (:readyShown = true and n.eventDate >= :readyOn))
              and (n.kind <> 'ACHIEVEMENT' or n.eventDate >= :achievementsSince)
            order by n.createdAt desc, n.id desc
            """)
    List<NotificationEntity> findShown(
            UUID profileId, LocalDate readyOn, boolean readyShown, LocalDate achievementsSince, Limit limit);

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

    /** 받는 사람(profile_id)으로 먼저 좁힌다 — about_profile_id 에는 인덱스가 없다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from NotificationEntity n
            where n.profileId in :recipientIds and n.aboutProfileId = :aboutProfileId and n.kind = :kind
            """)
    int deleteAbout(Collection<UUID> recipientIds, UUID aboutProfileId, String kind);

    /** {@link #deleteAbout} 와 같되 {@code keepDedupeKey} 의 알림은 남긴다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from NotificationEntity n
            where n.profileId in :recipientIds and n.aboutProfileId = :aboutProfileId and n.kind = :kind
              and n.dedupeKey <> :keepDedupeKey
            """)
    int deleteAboutExcept(Collection<UUID> recipientIds, UUID aboutProfileId, String kind, String keepDedupeKey);
}
