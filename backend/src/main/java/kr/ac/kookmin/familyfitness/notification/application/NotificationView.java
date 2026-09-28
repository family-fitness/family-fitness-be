package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationKind;
import org.jspecify.annotations.Nullable;

/**
 * 알림 한 건의 응답 모양 — fe:src/lib/api/types.ts NotificationView 그대로에 cheerId 를 더했다(ASKS 0-2 「알림에 cheerId」).
 * 문구(title · body)는 서버가 지은 그대로 화면이 내보낸다. 어느 화면으로 갈지는 kind 와 참조로 화면이 정한다.
 *
 * @param aboutProfileId 누구에 관한 알림인가. 부모 알림이면 그 아이
 * @param fromProfileId 보낸 사람 — 스티커 · 칭찬 · 고마워요. 아이가 고마워요를 돌려보낼 곳이다
 * @param date 그 일이 있었던 날(YYYY-MM-DD, KST). 측정 알림은 null
 * @param cheerId 알림을 만든 응원(KID_DONE · KID_THANKS · PRAISE). 고마워요 답장의 replyToCheerId 로 쓴다
 */
public record NotificationView(
        UUID notificationId,
        NotificationKind kind,
        String title,
        @Nullable String body,
        @Nullable UUID aboutProfileId,
        @Nullable UUID fromProfileId,
        @Nullable UUID missionId,
        @Nullable LocalDate date,
        @Nullable String stickerId,
        Instant createdAt,
        boolean read,
        @Nullable UUID cheerId) {
    static NotificationView of(Notification n) {
        return new NotificationView(
                n.id(),
                n.kind(),
                n.title(),
                n.body(),
                n.aboutProfileId(),
                n.fromProfileId(),
                n.missionId(),
                n.date(),
                n.stickerId(),
                n.createdAt(),
                n.isRead(),
                n.cheerId());
    }
}
