package kr.ac.kookmin.familyfitness.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 알림 한 건 — 한 사람(profileId)이 받은 것. 문구는 만든 순간 굳힌다(결정 45). 만드는 규칙(받는 사람 · 칸 · 멱등 키)은 FE 목
 * (fe:src/mocks/notifications.ts)의 항목 하나하나를 그대로 옮긴 아래 팩토리에 있다. 멱등 키도 목의 notificationId 모양이다.
 *
 * <pre>
 * 종류            받는 사람   about   from    mission cheer   sticker date        멱등 키
 * KID_DONE       부모       아이     -       응원의   응원     -       받은 날      done-{cheerId}
 * KID_THANKS     부모       아이     아이     -       응원     응원의   받은 날      thanks-{cheerId}
 * PRAISE         아이       그 아이  보낸 이  응원의   응원     응원의   받은 날      praise-{cheerId}
 * MISSION_READY  아이       그 아이  -       미션     -       -       선 날        ready-{missionId}-{date}
 * ACHIEVEMENT    아이       그 아이  -       -       -       -       받은 날      badge-{profileId}-{code}
 * REMEASURE      부모       아이     -       -       -       -       -           remeasure-{kidId}-{testedOn}
 * </pre>
 *
 * @param date 그 일이 있었던 날(KST). 화면이 캘린더 그날로 갈 때 쓴다
 * @param readAt 읽은 시각. 안 읽었으면 null
 * @param dedupeKey 같은 일로 같은 사람에게 두 번 만들지 않게 가르는 키
 */
public record Notification(
        UUID id,
        UUID profileId,
        NotificationKind kind,
        String title,
        @Nullable String body,
        @Nullable UUID aboutProfileId,
        @Nullable UUID fromProfileId,
        @Nullable UUID missionId,
        @Nullable UUID cheerId,
        @Nullable String stickerId,
        @Nullable LocalDate date,
        Instant createdAt,
        @Nullable Instant readAt,
        String dedupeKey) {

    public boolean isRead() {
        return readAt != null;
    }

    /** 아이가 「다 했어요」 를 알렸다(DONE 응원) → 그 응원을 받은 부모. */
    public static Notification kidDone(
            UUID parentId,
            UUID kidId,
            String kidName,
            UUID cheerId,
            @Nullable String message,
            @Nullable UUID missionId,
            LocalDate receivedOn,
            Instant createdAt) {
        return fresh(
                parentId,
                NotificationKind.KID_DONE,
                NotificationCopy.kidDoneTitle(kidName),
                message,
                kidId,
                null,
                missionId,
                cheerId,
                null,
                receivedOn,
                createdAt,
                "done-" + cheerId);
    }

    /** 아이가 고마워요 스티커를 보냈다(THANKS 응원) → 받은 부모. 고마워요에는 미션을 싣지 않는다(목과 같다). */
    public static Notification kidThanks(
            UUID parentId,
            UUID kidId,
            String kidName,
            UUID cheerId,
            @Nullable String stickerId,
            @Nullable String message,
            LocalDate receivedOn,
            Instant createdAt) {
        return fresh(
                parentId,
                NotificationKind.KID_THANKS,
                NotificationCopy.kidThanksTitle(kidName),
                NotificationCopy.kidThanksBody(stickerId, message),
                kidId,
                kidId,
                null,
                cheerId,
                stickerId,
                receivedOn,
                createdAt,
                "thanks-" + cheerId);
    }

    /** 부모가 칭찬 · 스티커를 보냈다(PRAISE 응원) → 받은 아이. {@code senderCall} 은 보낸 보호자의 프로필 이름. */
    public static Notification praise(
            UUID kidId,
            UUID senderId,
            String senderCall,
            UUID cheerId,
            @Nullable String stickerId,
            @Nullable String message,
            @Nullable UUID missionId,
            LocalDate receivedOn,
            Instant createdAt) {
        return fresh(
                kidId,
                NotificationKind.PRAISE,
                NotificationCopy.praiseTitle(senderCall, stickerId),
                message,
                kidId,
                senderId,
                missionId,
                cheerId,
                stickerId,
                receivedOn,
                createdAt,
                "praise-" + cheerId);
    }

    /** 그날 운동이 섰다 → 아직 안 끝낸 참여 아이. 미션 하나 · 날 하나에 한 건. */
    public static Notification missionReady(
            UUID kidId, UUID missionId, String missionTitle, LocalDate on, Instant createdAt) {
        return fresh(
                kidId,
                NotificationKind.MISSION_READY,
                NotificationCopy.missionReadyTitle(),
                missionTitle,
                kidId,
                null,
                missionId,
                null,
                null,
                on,
                createdAt,
                "ready-" + missionId + "-" + on);
    }

    /** 새 업적 → 받은 아이. createdAt 은 받은 시각. */
    public static Notification achievement(
            UUID kidId, String code, String title, String description, LocalDate earnedOn, Instant earnedAt) {
        return fresh(
                kidId,
                NotificationKind.ACHIEVEMENT,
                NotificationCopy.achievementTitle(title),
                NotificationCopy.earned(description),
                kidId,
                null,
                null,
                null,
                null,
                earnedOn,
                earnedAt,
                "badge-" + kidId + "-" + code);
    }

    /** 아이 측정이 30일 지났다 → 부모 한 사람. 측정 회차(마지막 testedOn)마다 한 번. 본문의 날수는 {@code today} 기준으로 굳는다. */
    public static Notification remeasure(
            UUID parentId, UUID kidId, String kidName, LocalDate lastTestedOn, LocalDate today, Instant createdAt) {
        return fresh(
                parentId,
                NotificationKind.REMEASURE,
                NotificationCopy.remeasureTitle(kidName),
                NotificationCopy.remeasureBody(ChronoUnit.DAYS.between(lastTestedOn, today)),
                kidId,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                remeasureKey(kidId, lastTestedOn));
    }

    /** REMEASURE 의 멱등 키 — 아이와 그 알림을 만든 측정 회차(마지막 testedOn). 부모마다 같은 키다. */
    public static String remeasureKey(UUID kidId, LocalDate lastTestedOn) {
        return "remeasure-" + kidId + "-" + lastTestedOn;
    }

    private static Notification fresh(
            UUID profileId,
            NotificationKind kind,
            String title,
            @Nullable String body,
            @Nullable UUID aboutProfileId,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            @Nullable UUID cheerId,
            @Nullable String stickerId,
            @Nullable LocalDate date,
            Instant createdAt,
            String dedupeKey) {
        return new Notification(
                UUID.randomUUID(),
                profileId,
                kind,
                title,
                body,
                aboutProfileId,
                fromProfileId,
                missionId,
                cheerId,
                stickerId,
                date,
                createdAt,
                null,
                dedupeKey);
    }
}
