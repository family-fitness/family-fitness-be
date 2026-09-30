package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Duration;
import java.time.Instant;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * 멈춘 RUNNING 정리. 코치 실행은 메모리 안의 스레드 풀 작업이라 서버가 도중에 죽으면 행이 RUNNING 으로 남아
 * 그 (프로필, 날짜) 잠금(lock_key)을 계속 쥔다(되돌리는 코드가 없었다).
 * 기동 때 한 번, 그 뒤로는 {@link CoachRunTimeLimit} 간격으로 그 한도보다 오래된 RUNNING 을 조건부 UPDATE 로
 * FAILED(STALE)로 바꾸고 잠금을 푼다.
 * 주기가 설정에서 계산한 값이라 @Scheduled 의 상수 대신 {@link SchedulingConfigurer} 로 등록한다(스케줄러는 같다).
 */
@Component
public class StaleCoachRunSweeper implements SchedulingConfigurer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoachRunRepository runs;
    private final CoachRunTimeLimit limit;
    private final AppTime time;

    public StaleCoachRunSweeper(CoachRunRepository runs, CoachRunTimeLimit limit, AppTime time) {
        this.runs = runs;
        this.limit = limit;
        this.time = time;
    }

    /** 기동 직후 한 번. 여기서 난 오류로 서버가 뜨지 못하면 안 되므로 로그만 남긴다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            sweep();
        } catch (RuntimeException e) {
            log.error("기동 때 멈춘 코치 실행 정리 실패", e);
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Duration every = limit.longestRunning();
        registrar.addFixedDelayTask(new FixedDelayTask(this::sweep, every, every));
    }

    /** 한도보다 오래된 RUNNING 을 FAILED 로 바꾸고 바꾼 행 수를 돌려준다. */
    public int sweep() {
        Instant now = time.now();
        int failed = runs.failRunningCreatedBefore(limit.staleBefore(now), limit.staleReason(), now);
        if (failed > 0) {
            log.warn(
                    "멈춘 코치 실행 {}건을 FAILED 로 정리했다(기준 {}초)",
                    failed,
                    limit.longestRunning().toSeconds());
        }
        return failed;
    }
}
