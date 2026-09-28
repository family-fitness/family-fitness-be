package kr.ac.kookmin.familyfitness.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 알림함 읽기 · 읽음 — 권한은 requireActingAs, 안 읽은 수는 돌려준 것 가운데서, 읽음은 upTo 까지. */
class NotificationServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Instant now = Instant.parse("2026-09-29T11:00:00Z"); // 20:00 KST
    private final LocalDate today = LocalDate.of(2026, 9, 29);
    private final UUID user = UUID.randomUUID();
    private final UUID mom = UUID.randomUUID();
    private final UUID kid = UUID.randomUUID();
    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final NotificationService service =
            new NotificationService(repository, familyAccess, Clock.fixed(now, KST), KST);

    private Notification done(int minutesAgo) {
        Notification n = Notification.kidDone(
                mom, kid, "서준", UUID.randomUUID(), "했어요", null, today, now.minusSeconds(60L * minutesAgo));
        repository.insertIfAbsent(n);
        return n;
    }

    @Test
    @DisplayName("최신 30건만, 늦은 것부터 — 안 읽은 수는 돌려준 30건 가운데서 센다(목과 같다)")
    void 최신_30건() {
        for (int i = 0; i < 32; i++) done(i);

        NotificationListView list = service.list(user, mom);

        assertThat(list.items()).hasSize(30);
        assertThat(list.items().getFirst().createdAt()).isEqualTo(now);
        assertThat(list.items().getLast().createdAt()).isEqualTo(now.minusSeconds(60L * 29));
        assertThat(list.unread()).isEqualTo(30);
    }

    @Test
    @DisplayName("upTo 가 있으면 그 시각까지 만든 것만 읽음 — 목록을 받은 뒤 온 알림은 안 읽은 채 남는다. 없으면 전부")
    void 읽음은_upTo_까지() {
        Notification older = done(10);
        done(0);

        service.markRead(user, mom, older.createdAt());
        assertThat(service.list(user, mom).unread()).isEqualTo(1);
        assertThat(service.list(user, mom).items())
                .extracting(NotificationView::read)
                .containsExactly(false, true);

        service.markRead(user, mom, null);
        assertThat(service.list(user, mom).unread()).isZero();
    }

    @Test
    @DisplayName("대신할 수 없는 프로필이면 identity 의 예외가 그대로 올라가고 아무것도 읽음이 되지 않는다")
    void 권한() {
        done(0);
        when(familyAccess.requireActingAs(user, mom)).thenThrow(new CannotActAsProfileException());

        assertThatThrownBy(() -> service.list(user, mom)).isInstanceOf(CannotActAsProfileException.class);
        assertThatThrownBy(() -> service.markRead(user, mom, null)).isInstanceOf(CannotActAsProfileException.class);
        assertThat(repository.rows).noneMatch(Notification::isRead);
    }
}
