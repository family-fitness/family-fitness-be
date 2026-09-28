package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationRules;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 읽기 · 읽음. 권한은 {@link FamilyAccess#requireActingAs} — 자기 프로필이거나, 보호자가 계정 없는 아이 프로필을 대신할 때만
 * 된다(아이 모드). 다른 가족 403 NOT_SAME_FAMILY, 같은 가족이어도 대신할 수 없는 프로필 403 FORBIDDEN, 없는 프로필 404.
 */
@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final FamilyAccess familyAccess;
    private final RestDayQuery restDays;
    private final Clock clock;
    private final ZoneId zone;

    public NotificationService(
            NotificationRepository notifications,
            FamilyAccess familyAccess,
            RestDayQuery restDays,
            Clock clock,
            ZoneId appZone) {
        this.notifications = notifications;
        this.familyAccess = familyAccess;
        this.restDays = restDays;
        this.clock = clock;
        this.zone = appZone;
    }

    /**
     * 보이는 알림 최신 30건({@link NotificationRules}). 오늘이 그 가족의 쉬는 날이면 오늘 서는 미션 알림(MISSION_READY)은 싣지 않는다
     * — 07:30 에 알림을 만든 뒤 쉬는 날 카드를 써도 쉬는 날 규칙(결정 45 「쉬는 날 제외」)과 어긋나지 않게 읽을 때 가른다.
     */
    @Transactional(readOnly = true)
    public NotificationListView list(UUID userId, UUID profileId) {
        ProfileSummary profile = familyAccess.requireActingAs(userId, profileId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        boolean restDay =
                restDays.restDaysBetween(profile.familyId(), today, today).contains(today);
        List<NotificationView> items = notifications
                .latest(
                        profileId,
                        today,
                        !restDay,
                        NotificationRules.achievementsShownSince(today),
                        NotificationRules.LIST_LIMIT)
                .stream()
                .map(NotificationView::of)
                .toList();
        int unread = (int) items.stream().filter(it -> !it.read()).count();
        return new NotificationListView(items, unread);
    }

    /**
     * 안 읽은 것을 읽음으로. {@code upTo} 가 있으면 만든 시각이 그때까지인 것만 — 화면이 받은 가장 새 createdAt 을 보내면,
     * 목록을 받은 뒤 새로 온 알림은 안 읽은 채 남는다(Q-rhythm-13). 없으면 전부(목과 같다).
     */
    public void markRead(UUID userId, UUID profileId, @Nullable Instant upTo) {
        familyAccess.requireActingAs(userId, profileId);
        notifications.markRead(profileId, upTo, clock.instant());
    }
}
