package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.IntSupplier;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCancelled;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCompleted;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCreated;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationRules;
import kr.ac.kookmin.familyfitness.progress.api.AchievementEarned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 다른 모듈의 일을 알림으로 바꾼다. <b>발행한 트랜잭션이 커밋된 뒤</b>(AFTER_COMMIT) 같은 스레드에서 받고, 쓰기는
 * {@link NotificationWriter} 의 새 트랜잭션에서 한다.
 *
 * <ul>
 *   <li>커밋 뒤라 알림이 실패해도 응원 · 칸 끝 · 미션 만들기는 되돌아가지 않는다. 실패는 여기서 잡아 로그만 남긴다 — 커밋 뒤 콜백의
 *       예외는 요청까지 올라가 이미 저장된 일을 500 으로 답하게 만든다.
 *   <li>비동기(@Async)로 넘기지 않는다. FE 는 응원 · 승인 · 칸 끝 직후 알림을 다시 읽으므로 응답 전에 저장돼 있어야 한다.
 *   <li>트랜잭션 밖에서 발행된 이벤트도 버리지 않고 곧바로 처리한다(fallbackExecution).
 * </ul>
 */
@Component
public class NotificationEventListener {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NotificationWriter writer;
    private final Clock clock;
    private final ZoneId zone;

    public NotificationEventListener(NotificationWriter writer, Clock clock, ZoneId appZone) {
        this.writer = writer;
        this.clock = clock;
        this.zone = appZone;
    }

    /** 응원 → KID_DONE · KID_THANKS · PRAISE. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CheerSent cheer) {
        run("응원", cheer.cheerId(), () -> writer.fromCheer(cheer));
    }

    /**
     * 미션이 생겼다 → 오늘이 기간 안이고 오늘 07:30(KST)이 지났으면 그 자리에서 MISSION_READY. 07:30 전이면 07:30 스케줄러가 만든다.
     * 지금 시각으로 가른다 — 07:30 직전에 만들어 07:30 이 지나 커밋된 미션을 스케줄러와 여기 둘 다 놓치지 않게.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MissionCreated created) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (today.isBefore(created.startDate()) || today.isAfter(created.endDate())) return;
        if (now.isBefore(
                today.atTime(NotificationRules.MISSION_READY_AT).atZone(zone).toInstant())) return;
        run(
                "새 미션",
                created.missionId(),
                () -> writer.missionReadyForMission(created.missionId(), today, created.createdAt()));
    }

    /** 미션을 끝까지 했다 → 그 사람의 그 미션 MISSION_READY 를 뺀다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MissionCompleted completed) {
        run("미션 끝", completed.missionId(), () -> writer.missionCompleted(completed.profileId(), completed.missionId()));
    }

    /** 미션이 지워졌다 → 그 미션의 알림을 지운다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MissionCancelled cancelled) {
        run("미션 지움", cancelled.missionId(), () -> writer.missionCancelled(cancelled.missionId()));
    }

    /** 새 업적 → ACHIEVEMENT(아이만). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AchievementEarned earned) {
        run("업적 " + earned.code(), earned.profileId(), () -> writer.achievement(earned));
    }

    private void run(String what, Object id, IntSupplier job) {
        try {
            job.getAsInt();
        } catch (RuntimeException e) {
            log.error("알림을 만들지 못했다({} {}) — 원래 일은 이미 저장됐다", what, id, e);
        }
    }
}
