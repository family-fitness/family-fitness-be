package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationKind;
import org.jspecify.annotations.Nullable;

/** 알림 메모리 저장소. 보이는 알림 거르기는 JPA 쿼리와 같은 조건이다(실제 쿼리는 웹 시험이 H2 로 본다). */
class InMemoryNotificationRepository implements NotificationRepository {
    final List<Notification> rows = new ArrayList<>();
    /** REMEASURE 지우기를 부른 횟수 — 지울 것이 없는 측정에 쿼리를 돌리지 않는지 본다. */
    int remeasureDeletes;

    @Override
    public boolean insertIfAbsent(Notification n) {
        boolean exists = rows.stream()
                .anyMatch(it ->
                        it.profileId().equals(n.profileId()) && it.dedupeKey().equals(n.dedupeKey()));
        if (exists) return false;
        rows.add(n);
        return true;
    }

    @Override
    public List<Notification> latest(
            UUID profileId, LocalDate readyOn, boolean missionReadyShown, LocalDate achievementsSince, int limit) {
        return rows.stream()
                .filter(it -> it.profileId().equals(profileId))
                .filter(it -> it.kind() != NotificationKind.MISSION_READY
                        || (missionReadyShown && !dateOf(it).isBefore(readyOn)))
                .filter(it ->
                        it.kind() != NotificationKind.ACHIEVEMENT || !dateOf(it).isBefore(achievementsSince))
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public int markRead(UUID profileId, @Nullable Instant upTo, Instant readAt) {
        int changed = 0;
        for (int i = 0; i < rows.size(); i++) {
            Notification n = rows.get(i);
            if (!n.profileId().equals(profileId) || n.isRead()) continue;
            if (upTo != null && n.createdAt().isAfter(upTo)) continue;
            rows.set(i, withReadAt(n, readAt));
            changed++;
        }
        return changed;
    }

    @Override
    public int deleteMissionReady(UUID profileId, UUID missionId) {
        int before = rows.size();
        rows.removeIf(it -> it.profileId().equals(profileId)
                && missionId.equals(it.missionId())
                && it.kind() == NotificationKind.MISSION_READY);
        return before - rows.size();
    }

    @Override
    public int deleteByMission(UUID missionId) {
        int before = rows.size();
        rows.removeIf(it -> missionId.equals(it.missionId()));
        return before - rows.size();
    }

    @Override
    public int deleteRemeasureAbout(Collection<UUID> recipientIds, UUID kidId, @Nullable String keepDedupeKey) {
        remeasureDeletes++;
        int before = rows.size();
        rows.removeIf(it -> recipientIds.contains(it.profileId())
                && it.kind() == NotificationKind.REMEASURE
                && kidId.equals(it.aboutProfileId())
                && !it.dedupeKey().equals(keepDedupeKey));
        return before - rows.size();
    }

    List<Notification> of(UUID profileId) {
        return rows.stream().filter(it -> it.profileId().equals(profileId)).toList();
    }

    private static LocalDate dateOf(Notification n) {
        LocalDate date = n.date();
        return date == null ? LocalDate.MIN : date;
    }

    private static Notification withReadAt(Notification n, Instant readAt) {
        return new Notification(
                n.id(),
                n.profileId(),
                n.kind(),
                n.title(),
                n.body(),
                n.aboutProfileId(),
                n.fromProfileId(),
                n.missionId(),
                n.cheerId(),
                n.stickerId(),
                n.date(),
                n.createdAt(),
                readAt,
                n.dedupeKey());
    }
}
