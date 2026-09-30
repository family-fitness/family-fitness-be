package kr.ac.kookmin.familyfitness.notification.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림 쓰기 전용 스레드 풀(결정 51 · SA-01). 커밋 뒤 콜백(AFTER_COMMIT)은 원래 트랜잭션의 커넥션을 아직 쥔 채 돈다. 거기서 알림을 새
 * 트랜잭션으로 쓰면 요청 스레드 하나가 커넥션 두 개를 쥐고, 동시 요청이 풀 크기를 넘으면 모두 두 번째 커넥션을 기다리다 연결 대기
 * 시간만큼 멈춘 뒤 알림이 빠진다. 그래서 콜백은 이 풀에 일을 넘기기만 하고, 알림은 이 풀의 스레드가 커넥션 하나로 쓴다.
 *
 * <ul>
 *   <li>스레드 수 고정(core = max) — 알림이 동시에 쥐는 커넥션도 이 수를 넘지 않는다.
 *   <li>대기열에 상한을 둔다. 대기열까지 차면 그 알림은 로그를 남기고 버린다(원래 일은 이미 커밋됐다).
 *   <li>서버를 내릴 때 대기열에 남은 알림을 끝까지 쓰고 내린다(최대 10초).
 *   <li>{@code app.notification.executor.async=false} 면 부른 스레드에서 곧바로 쓴다 — 시험 프로필 전용. 커밋한 행을 시험 끝에
 *       지우는 시험들이 뒤늦게 들어온 알림 행과 엇갈리지 않게 한다.
 * </ul>
 *
 * 기동 때 그날 몫 따라잡기({@link NotificationScheduler#onReady})도 이 풀에서 돈다(main 스레드를 붙잡지 않게).
 */
@Configuration(proxyBeanMethods = false)
public class NotificationExecutorConfig {
    public static final String EXECUTOR = "notificationTaskExecutor";

    @Bean(EXECUTOR)
    public TaskExecutor notificationTaskExecutor(
            @Value("${app.notification.executor.async:true}") boolean async,
            @Value("${app.notification.executor.pool-size:2}") int poolSize,
            @Value("${app.notification.executor.queue-capacity:1000}") int queueCapacity) {
        if (!async) return new SyncTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notification-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
