package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMissionQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 두 번 만드는 알림. 가족마다 따로 쓰고(가족 하나가 실패해도 나머지는 계속한다), 몇 번 돌아도 결과가 같다(멱등 키).
 *
 * <pre>
 * 07:30 KST  MISSION_READY — 오늘 기간이 걸친 미션이 있는 가족만(쿼리 한 번으로 고른다) · 쉬는 날 제외 · 안 끝낸 아이
 * 09:00 KST  REMEASURE     — 모든 가족. 마지막 측정에서 30일 이상 지난 아이 → 부모 전원
 * </pre>
 *
 * 만든 시각은 정해진 시각(07:30 · 09:00)이고, 그보다 늦게 돈 실행(기동 따라잡기 · 밀린 실행)이면 가족을 쓰는 그때다 — 그 전에 받은
 * 목록의 upTo 로 읽음 처리할 때 보지도 못한 알림이 함께 읽음이 되지 않게.
 * 기동 때도 한 번 돌려 그날 몫을 따라잡는다(그 시각이 이미 지났을 때만 — 07:30 전에 뜬 서버는 07:30 을 기다린다). 따라잡기는 알림
 * 전용 스레드 풀에서 돈다 — main 스레드가 붙잡혀 기동이 늦어지지 않게.
 */
@Component
public class NotificationScheduler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ProfileQuery profiles;
    private final StandingMissionQuery standingMissions;
    private final NotificationWriter writer;
    private final TaskExecutor executor;
    private final Clock clock;
    private final ZoneId zone;

    public NotificationScheduler(
            ProfileQuery profiles,
            StandingMissionQuery standingMissions,
            NotificationWriter writer,
            @Qualifier(NotificationExecutorConfig.EXECUTOR) TaskExecutor executor,
            Clock clock,
            ZoneId appZone) {
        this.profiles = profiles;
        this.standingMissions = standingMissions;
        this.writer = writer;
        this.executor = executor;
        this.clock = clock;
        this.zone = appZone;
    }

    /** 기동 직후 그날 몫 따라잡기를 알림 스레드 풀에 넘긴다. 여기서 난 오류로 서버가 뜨지 못하면 안 되므로 로그만 남긴다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            executor.execute(this::catchUp);
        } catch (TaskRejectedException e) {
            log.error("기동 때 알림 따라잡기를 시작하지 못했다", e);
        }
    }

    private void catchUp() {
        try {
            LocalDate today = today();
            if (passed(today, NotificationRules.MISSION_READY_AT)) missionReady();
            if (passed(today, NotificationRules.REMEASURE_AT)) remeasure();
        } catch (RuntimeException e) {
            log.error("기동 때 알림 따라잡기 실패", e);
        }
    }

    /** 오늘 서는 미션 알림. 만든 시각은 오늘 07:30(KST), 늦게 돌면 그때. */
    @Scheduled(cron = "${app.notification.mission-ready-cron:0 30 7 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public Run missionReady() {
        LocalDate today = today();
        Instant readyAt = at(today, NotificationRules.MISSION_READY_AT);
        return forEachFamily(
                "MISSION_READY",
                standingMissions.familiesWithMissionsOn(today),
                familyId -> writer.missionReadyForFamily(familyId, today, notBefore(readyAt)));
    }

    /** 다시 재기 알림. 만든 시각은 오늘 09:00(KST), 늦게 돌면 그때. */
    @Scheduled(cron = "${app.notification.remeasure-cron:0 0 9 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public Run remeasure() {
        LocalDate today = today();
        Instant at = at(today, NotificationRules.REMEASURE_AT);
        return forEachFamily(
                "REMEASURE",
                profiles.allFamilyIds(),
                familyId -> writer.remeasureForFamily(familyId, today, notBefore(at)));
    }

    private Run forEachFamily(String kind, List<UUID> familyIds, FamilyJob job) {
        int made = 0;
        int failed = 0;
        for (UUID familyId : familyIds) {
            try {
                made += job.run(familyId);
            } catch (RuntimeException e) {
                failed++;
                log.error("{} 알림을 만들지 못했다: 가족 {}", kind, familyId, e);
            }
        }
        if (made > 0 || failed > 0) log.info("{} 알림 {}건을 만들었다 — 실패한 가족 {}곳", kind, made, failed);
        return new Run(made, failed);
    }

    /** 정해진 시각과 지금 가운데 늦은 쪽. 제때 돈 실행은 정해진 시각 그대로다. */
    private Instant notBefore(Instant scheduled) {
        Instant now = clock.instant();
        return now.isAfter(scheduled) ? now : scheduled;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    private boolean passed(LocalDate today, LocalTime time) {
        return !clock.instant().isBefore(at(today, time));
    }

    private Instant at(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(zone).toInstant();
    }

    @FunctionalInterface
    private interface FamilyJob {
        int run(UUID familyId);
    }

    /**
     * 한 번 돈 결과.
     *
     * @param created 새로 만든 알림 수(이미 있던 것은 세지 않는다)
     * @param failedFamilies 실패한 가족 수
     */
    public record Run(int created, int failedFamilies) {}
}
