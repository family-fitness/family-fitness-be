package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.IntSupplier;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCancelled;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCompleted;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCreated;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationRules;
import kr.ac.kookmin.familyfitness.progress.api.AchievementEarned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 다른 모듈의 일을 알림으로 바꾼다. <b>발행한 트랜잭션이 커밋된 뒤</b>(AFTER_COMMIT) 받아 알림 전용 스레드 풀
 * ({@link NotificationExecutorConfig})에 넘기고, 쓰기는 그 풀의 스레드가 {@link NotificationWriter} 의 트랜잭션에서 한다.
 *
 * <ul>
 *   <li>커밋 뒤라 알림이 실패해도 응원 · 칸 끝 · 미션 만들기 · 측정은 되돌아가지 않는다. 실패는 풀 스레드에서 잡아 로그만 남긴다.
 *   <li>요청 스레드에서 쓰지 않는다 — 커밋 뒤 콜백은 원래 트랜잭션의 커넥션을 아직 쥐고 있어, 거기서 새 트랜잭션을 열면 요청 하나가
 *       커넥션 두 개를 쥔다(동시 요청이 풀을 넘으면 서로 기다리다 멈춘다 — SA-01). 그래서 알림은 커밋 뒤 조금 늦게 생긴다.
 *   <li>풀의 대기열까지 차면 그 알림은 버리고 로그를 남긴다.
 *   <li>트랜잭션 밖에서 발행된 이벤트도 버리지 않고 곧바로 넘긴다(fallbackExecution).
 * </ul>
 */
@Component
public class NotificationEventListener {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NotificationWriter writer;
    private final TaskExecutor executor;
    private final Clock clock;
    private final ZoneId zone;

    public NotificationEventListener(
            NotificationWriter writer,
            @Qualifier(NotificationExecutorConfig.EXECUTOR) TaskExecutor executor,
            Clock clock,
            ZoneId appZone) {
        this.writer = writer;
        this.executor = executor;
        this.clock = clock;
        this.zone = appZone;
    }

    /** 응원 → KID_DONE · KID_THANKS · PRAISE. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CheerSent cheer) {
        submit("응원", cheer.cheerId(), () -> writer.fromCheer(cheer));
    }

    /**
     * 미션이 생겼다 → 오늘이 기간 안이고 오늘 07:30(KST)이 지났으면 곧바로 MISSION_READY. 07:30 전이면 07:30 스케줄러가 만든다.
     * 커밋된 지금 시각으로 가른다 — 07:30 직전에 만들어 07:30 이 지나 커밋된 미션을 스케줄러와 여기 둘 다 놓치지 않게.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MissionCreated created) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (today.isBefore(created.startDate()) || today.isAfter(created.endDate())) return;
        if (now.isBefore(
                today.atTime(NotificationRules.MISSION_READY_AT).atZone(zone).toInstant())) return;
        submit(
                "새 미션",
                created.missionId(),
                () -> writer.missionReadyForMission(created.missionId(), today, created.createdAt()));
    }

    /** 미션을 끝까지 했다 → 그 사람의 그 미션 MISSION_READY 를 뺀다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MissionCompleted completed) {
        submit(
                "미션 끝",
                completed.missionId(),
                () -> writer.missionCompleted(completed.profileId(), completed.missionId()));
    }

    /** 미션이 지워졌다 → 그 미션의 알림을 지운다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MissionCancelled cancelled) {
        submit("미션 지움", cancelled.missionId(), () -> writer.missionCancelled(cancelled.missionId()));
    }

    /** 새 업적 → ACHIEVEMENT(아이만). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AchievementEarned earned) {
        submit("업적 " + earned.code(), earned.profileId(), () -> writer.achievement(earned));
    }

    /** 측정 회차가 저장됐다 → 그 사람의 지난 회차로 만든 REMEASURE 를 뺀다(목은 늘 마지막 측정일로 셈한다 — SA-02). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(FitnessTestRegistered registered) {
        submit("다시 잼", registered.profileId(), () -> writer.remeasured(registered.profileId()));
    }

    private void submit(String what, Object id, IntSupplier job) {
        try {
            executor.execute(() -> run(what, id, job));
        } catch (TaskRejectedException e) {
            log.warn("알림 대기열이 가득 차 알림을 버렸다({} {}) — 원래 일은 이미 저장됐다: {}", what, id, e.getMessage());
        }
    }

    private void run(String what, Object id, IntSupplier job) {
        try {
            job.getAsInt();
        } catch (RuntimeException e) {
            log.error("알림을 만들지 못했다({} {}) — 원래 일은 이미 저장됐다", what, id, e);
        }
    }
}
