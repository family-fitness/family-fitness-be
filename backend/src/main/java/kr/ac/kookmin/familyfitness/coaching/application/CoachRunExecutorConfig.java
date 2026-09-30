package kr.ac.kookmin.familyfitness.coaching.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 편성 전용 스레드 풀. 편성 한 회차는 AI 폴링 동안(최대 {@link CoachRunTimeLimit}) 스레드 하나를 쥐므로 다른 비동기 작업과 풀을 나눠 쓰지 않는다.
 * 스레드가 다 차면 거절하지 않고 대기열에 넣는다(core = max 라 스레드를 더 늘리지 않는다).
 * 대기열까지 차면 {@link CoachRunExecutor} 가 그 실행을 곧바로 FAILED(BUSY)로 끝낸다.
 * 기본값은 이 풀을 두기 전에 쓰던 Spring Boot 기본 실행기와 같다(spring.task.execution.pool: 스레드 8 · 대기열 Integer.MAX_VALUE).
 * 대기열에서 기다린 시간도 {@link CoachRunTimeLimit} 에 들어가므로, 한도보다 오래 기다린 실행은 정리 작업이 FAILED(STALE)로 끝낸다.
 * 이 빈도 Executor 라 Boot 기본 실행기(applicationTaskExecutor)가 빠지지 않도록 spring.task.execution.mode=force 를 둔다.
 */
@Configuration(proxyBeanMethods = false)
public class CoachRunExecutorConfig {
    public static final String EXECUTOR = "coachRunTaskExecutor";

    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor coachRunTaskExecutor(
            @Value("${app.coach.executor.pool-size:8}") int poolSize,
            @Value("${app.coach.executor.queue-capacity:2147483647}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        // Boot 기본 실행기와 같게 쉬는 스레드는 60초 뒤 거둔다
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("coach-run-");
        return executor;
    }
}
