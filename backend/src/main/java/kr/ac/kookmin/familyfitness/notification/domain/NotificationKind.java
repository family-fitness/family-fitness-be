package kr.ac.kookmin.familyfitness.notification.domain;

/**
 * 알림 종류 여섯. 누구에게 가는지와 모양은 FE 목(fe:src/mocks/notifications.ts)과 FE 타입 NotificationKind 그대로다.
 *
 * <pre>
 * KID_DONE       부모에게 — 아이가 「다 했어요」 를 알렸다(DONE 응원)
 * KID_THANKS     부모에게 — 아이가 고마워요 스티커를 보냈다(THANKS 응원)
 * PRAISE         아이에게 — 부모가 칭찬 · 스티커를 보냈다(PRAISE 응원)
 * MISSION_READY  아이에게 — 그날 운동이 섰다
 * ACHIEVEMENT    아이에게 — 새 업적
 * REMEASURE      부모에게 — 아이 측정이 30일 지났다
 * </pre>
 */
public enum NotificationKind {
    KID_DONE,
    KID_THANKS,
    PRAISE,
    MISSION_READY,
    ACHIEVEMENT,
    REMEASURE
}
