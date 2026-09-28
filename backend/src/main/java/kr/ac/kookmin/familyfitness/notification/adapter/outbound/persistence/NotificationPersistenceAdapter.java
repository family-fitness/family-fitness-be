package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationKind;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link NotificationRepository} 의 JPA 구현. */
@Repository
@Transactional(readOnly = true)
public class NotificationPersistenceAdapter implements NotificationRepository {
    private final NotificationJpaRepository jpa;

    public NotificationPersistenceAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * 같은 키가 있는지 먼저 본다(경험치 원장과 같은 방식). 같은 트랜잭션에서 앞서 넣은 행도 보인다(조회 전에 JPA 가 미룬 insert 를
     * 내보낸다). 동시에 들어온 두 쓰기가 함께 검사를 지나치면 늦은 쪽의 insert 가 유니크 제약에 걸린다.
     */
    @Override
    @Transactional
    public boolean insertIfAbsent(Notification n) {
        if (jpa.existsByProfileIdAndDedupeKey(n.profileId(), n.dedupeKey())) return false;
        jpa.save(new NotificationEntity(
                n.id(),
                n.profileId(),
                n.kind().name(),
                n.title(),
                n.body(),
                n.aboutProfileId(),
                n.fromProfileId(),
                n.missionId(),
                n.cheerId(),
                n.stickerId(),
                n.date(),
                n.createdAt(),
                n.readAt(),
                n.dedupeKey()));
        return true;
    }

    @Override
    public List<Notification> latest(UUID profileId, LocalDate readyOn, LocalDate achievementsSince, int limit) {
        return jpa.findShown(profileId, readyOn, achievementsSince, Limit.of(limit)).stream()
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
