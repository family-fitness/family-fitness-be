package kr.ac.kookmin.familyfitness.notification.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * 알림을 언제 만들고 알림함에 무엇을 보이는가. 값은 FE 목(fe:src/mocks/notifications.ts)과 결정 45 그대로다.
 *
 * <pre>
 * 알림함        최신 30건, 만든 시각이 늦은 것부터. 안 읽은 수는 돌려준 것 가운데서 센다
 * MISSION_READY 그날 07:30(KST)에 만든다. 07:30 뒤에 생긴 미션은 생길 때. 그날 것만 보인다(목은 오늘 것만 셈한다)
 * ACHIEVEMENT   받은 날부터 14일 동안 보인다(목 「두 주 안에 받은 것」)
 * REMEASURE     그날 09:00(KST)에, 마지막 측정일에서 30일 이상 지났으면(목 days &lt; 30 이면 건너뜀 — 홈 카드와 같은 기준)
 * </pre>
 */
public final class NotificationRules {
    /** 알림함 한 번에 주는 건수. */
    public static final int LIST_LIMIT = 30;

    /** MISSION_READY 를 만드는 시각(KST). 스케줄러 cron 기본값과 같다. */
    public static final LocalTime MISSION_READY_AT = LocalTime.of(7, 30);

    /** REMEASURE 를 만드는 시각(KST). 스케줄러 cron 기본값과 같다. */
    public static final LocalTime REMEASURE_AT = LocalTime.of(9, 0);

    /** 마지막 측정 뒤 이만큼 지나면 다시 재자고 알린다. */
    public static final int REMEASURE_AFTER_DAYS = 30;

    /** 업적 알림이 알림함에 남는 날수. */
    public static final int ACHIEVEMENT_SHOWN_DAYS = 14;

    private NotificationRules() {}

    /** 마지막 측정일에서 30일 이상 지났는가. */
    public static boolean remeasureDue(LocalDate lastTestedOn, LocalDate today) {
        return ChronoUnit.DAYS.between(lastTestedOn, today) >= REMEASURE_AFTER_DAYS;
    }

    /** 이 날 이후(같은 날 포함)에 받은 업적 알림만 보인다. */
    public static LocalDate achievementsShownSince(LocalDate today) {
        return today.minusDays(ACHIEVEMENT_SHOWN_DAYS);
    }
}
