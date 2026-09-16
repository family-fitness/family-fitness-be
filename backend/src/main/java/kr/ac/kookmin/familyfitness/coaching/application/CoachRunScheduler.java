package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주간 코치 자동 실행 — 일요일 20:00(KST), 가족 단위(보드 F3 「스케줄 일요일 20:00」).
 * 가족마다 {@link CoachRunService#startScheduled} 를 부르고, 편성은 평소처럼 비동기 파이프라인이 한다.
 * `app.coach.schedule.enabled=false` 로 끌 수 있다(테스트·로컬 기본은 켜져 있지만 일요일 20시에만 돈다).
 */
@Component
@ConditionalOnProperty(name = "app.coach.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class CoachRunScheduler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ProfileQuery profileQuery;
    private final CoachRunService coachRuns;

    public CoachRunScheduler(ProfileQuery profileQuery, CoachRunService coachRuns) {
        this.profileQuery = profileQuery;
        this.coachRuns = coachRuns;
    }

    @Scheduled(cron = "${app.coach.schedule.cron:0 0 20 * * SUN}", zone = "${app.timezone:Asia/Seoul}")
    public void runWeekly() {
        List<UUID> families = profileQuery.allFamilyIds();
        int started = 0;
        for (UUID familyId : families) {
            try {
                if (coachRuns.startScheduled(familyId) != null) started++;
            } catch (RuntimeException e) {
                log.error("주간 코치 스케줄 실패: family={}", familyId, e);
            }
        }
        log.info("주간 코치 스케줄: 가족 {}곳 중 {}곳 실행", families.size(), started);
    }
}
