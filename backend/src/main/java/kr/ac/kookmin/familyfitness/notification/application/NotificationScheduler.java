package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationRules;
import kr.ac.kookmin.familyfitness.shared.persistence.SqlErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 두 번 만드는 알림. 가족마다 따로 쓰고(가족 하나가 실패해도 나머지는 계속한다), 몇 번 돌아도 결과가 같다(멱등 키).
 *
 * <pre>
 * 07:30 KST  MISSION_READY — 오늘 서는 미션 · 쉬는 날 제외 · 안 끝낸 아이
 * 09:00 KST  REMEASURE     — 마지막 측정에서 30일 이상 지난 아이 → 부모 전원
 * </pre>
 *
 * 기동 때도 한 번 돌려 그날 몫을 따라잡는다(그 시각이 이미 지났을 때만 — 07:30 전에 뜬 서버는 07:30 을 기다린다).
 * 같은 알림을 이벤트 리스너가 동시에 넣어 유니크 제약에 걸리면 그 가족을 한 번 더 쓴다(두 번째는 이미 들어간 것을 건너뛴다).
 */
@Component
public class NotificationScheduler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ProfileQuery profiles;
    private final NotificationWriter writer;
    private final Clock clock;
    private final ZoneId zone;

    public NotificationScheduler(ProfileQuery profiles, NotificationWriter writer, Clock clock, ZoneId appZone) {
        this.profiles = profiles;
        this.writer = writer;
        this.clock = clock;
        this.zone = appZone;
    }

    /** 기동 직후 그날 몫 따라잡기. 여기서 난 오류로 서버가 뜨지 못하면 안 되므로 로그만 남긴다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        LocalDate today = today();
        try {
            if (passed(today, NotificationRules.MISSION_READY_AT)) missionReady();
            if (passed(today, NotificationRules.REMEASURE_AT)) remeasure();
        } catch (RuntimeException e) {
            log.error("기동 때 알림 따라잡기 실패", e);
        }
    }

    /** 오늘 서는 미션 알림. 만든 시각은 오늘 07:30(KST)이다. */
    @Scheduled(cron = "${app.notification.mission-ready-cron:0 30 7 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public Run missionReady() {
        LocalDate today = today();
        Instant readyAt = at(today, NotificationRules.MISSION_READY_AT);
        return forEachFamily("MISSION_READY", familyId -> writer.missionReadyForFamily(familyId, today, readyAt));
    }

    /** 다시 재기 알림. 만든 시각은 오늘 09:00(KST)이다. */
    @Scheduled(cron = "${app.notification.remeasure-cron:0 0 9 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public Run remeasure() {
        LocalDate today = today();
        Instant at = at(today, NotificationRules.REMEASURE_AT);
        return forEachFamily("REMEASURE", familyId -> writer.remeasureForFamily(familyId, today, at));
    }

    private Run forEachFamily(String kind, FamilyJob job) {
        int made = 0;
        int failed = 0;
        for (UUID familyId : profiles.allFamilyIds()) {
            try {
                made += runOnce(job, familyId);
            } catch (RuntimeException e) {
                failed++;
                log.error("{} 알림을 만들지 못했다: 가족 {}", kind, familyId, e);
            }
        }
        if (made > 0 || failed > 0) log.info("{} 알림 {}건을 만들었다 — 실패한 가족 {}곳", kind, made, failed);
        return new Run(made, failed);
    }

    /** 이벤트 리스너와 같은 알림을 동시에 넣어 유니크 제약에 걸렸으면 한 번 더. 두 번째는 이미 들어간 알림을 건너뛴다. */
    private static int runOnce(FamilyJob job, UUID familyId) {
        try {
            return job.run(familyId);
        } catch (RuntimeException e) {
            if (!SqlErrors.isUniqueViolation(e)) throw e;
            return job.run(familyId);
        }
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
