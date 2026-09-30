package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationKind;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link NotificationRepository} 의 JPA 구현. 넣기만 JDBC 로 한다(아래 {@link #insertIfAbsent}). */
@Repository
@Transactional(readOnly = true)
public class NotificationPersistenceAdapter implements NotificationRepository {
    /**
     * 이미 같은 (profile_id, dedupe_key)가 있으면 아무것도 하지 않는다. 먼저 읽고 넣으면 같은 알림을 함께 넣은 두 트랜잭션이 둘 다
     * 「없음」 을 보고 늦은 쪽이 유니크 제약에 걸려 실패하므로, 충돌을 DB 가 삼키게 한다(찜 · 느낌과 같은 방식).
     * ON CONFLICT DO NOTHING 은 PostgreSQL 과 H2(MODE=PostgreSQL) 둘 다 받는다.
     */
    private static final String INSERT_IF_ABSENT = """
            insert into notifications (id, profile_id, kind, title, body, about_profile_id, from_profile_id, mission_id,
                                       cheer_id, sticker_id, event_date, created_at, read_at, dedupe_key)
            values (:id, :profileId, :kind, :title, :body, :aboutProfileId, :fromProfileId, :missionId,
                    :cheerId, :stickerId, :eventDate, :createdAt, :readAt, :dedupeKey)
            on conflict do nothing
            """;

    private final NotificationJpaRepository jpa;
    private final JdbcClient jdbc;

    public NotificationPersistenceAdapter(NotificationJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    /**
     * JPA 로는 ON CONFLICT 를 낼 수 없어 JDBC 로 넣는다(같은 트랜잭션 · 같은 커넥션이다). 빈 칸(null)은 Spring JDBC 가 드라이버에 맞게
     * 넘긴다 — PostgreSQL 은 타입 없는 NULL 로 보내 칸 타입을 서버가 정한다. 시각은 {@link OffsetDateTime} 으로 넘긴다 — PostgreSQL
     * JDBC 는 {@code Instant} 의 SQL 타입을 알아내지 못한다.
     */
    @Override
    @Transactional
    public boolean insertIfAbsent(Notification n) {
        int inserted = jdbc.sql(INSERT_IF_ABSENT)
                .param("id", n.id())
                .param("profileId", n.profileId())
                .param("kind", n.kind().name())
                .param("title", n.title())
                .param("body", n.body())
                .param("aboutProfileId", n.aboutProfileId())
                .param("fromProfileId", n.fromProfileId())
                .param("missionId", n.missionId())
                .param("cheerId", n.cheerId())
                .param("stickerId", n.stickerId())
                .param("eventDate", n.date())
                .param("createdAt", utc(n.createdAt()))
                .param("readAt", n.readAt() == null ? null : utc(n.readAt()))
                .param("dedupeKey", n.dedupeKey())
                .update();
        return inserted > 0;
    }

    @Override
    public List<Notification> latest(
            UUID profileId, LocalDate readyOn, boolean missionReadyShown, LocalDate achievementsSince, int limit) {
        return jpa.findShown(profileId, readyOn, missionReadyShown, achievementsSince, Limit.of(limit)).stream()
                .map(NotificationPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int markRead(UUID profileId, @Nullable Instant upTo, Instant readAt) {
        return upTo == null ? jpa.markAllRead(profileId, readAt) : jpa.markReadUpTo(profileId, upTo, readAt);
    }

    @Override
    @Transactional
    public int deleteMissionReady(UUID profileId, UUID missionId) {
        return jpa.deleteOfProfileMission(profileId, missionId, NotificationKind.MISSION_READY.name());
    }

    @Override
    @Transactional
    public int deleteByMission(UUID missionId) {
        return jpa.deleteOfMission(missionId);
    }

    @Override
    @Transactional
    public int deleteRemeasureAbout(Collection<UUID> recipientIds, UUID kidId, @Nullable String keepDedupeKey) {
        if (recipientIds.isEmpty()) return 0;
        String kind = NotificationKind.REMEASURE.name();
        return keepDedupeKey == null
                ? jpa.deleteAbout(recipientIds, kidId, kind)
                : jpa.deleteAboutExcept(recipientIds, kidId, kind, keepDedupeKey);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Notification toDomain(NotificationEntity e) {
        return new Notification(
                e.getId(),
                e.getProfileId(),
                NotificationKind.valueOf(e.getKind()),
                e.getTitle(),
                e.getBody(),
                e.getAboutProfileId(),
                e.getFromProfileId(),
                e.getMissionId(),
                e.getCheerId(),
                e.getStickerId(),
                e.getEventDate(),
                e.getCreatedAt(),
                e.getReadAt(),
                e.getDedupeKey());
    }
}
