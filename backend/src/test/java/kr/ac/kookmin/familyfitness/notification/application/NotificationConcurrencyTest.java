package kr.ac.kookmin.familyfitness.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 쓰기가 DB 커넥션 풀과 겹칠 때 — 풀을 2개로 줄이고(연결 대기 1초) 실제로 커밋하며 본다(SA-01). 운영처럼 알림은 전용 스레드 풀에서
 * 비동기로 쓴다(시험 프로필의 동기 실행을 이 클래스에서만 끈다). identity 는 실제 구현이라 식구 행을 {@link ProfileRows} 로 꽂고,
 * 끝나면 이 가족의 행을 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=2",
            "spring.datasource.hikari.connection-timeout=" + NotificationConcurrencyTest.CONNECTION_TIMEOUT_MILLIS,
            "app.notification.executor.async=true"
        })
class NotificationConcurrencyTest {
    static final long CONNECTION_TIMEOUT_MILLIS = 1000;

    /** 풀(2개)보다 많은 요청을 한꺼번에 커밋한다. */
    private static final int REQUESTS = 6;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    @Qualifier(NotificationExecutorConfig.EXECUTOR)
    TaskExecutor notificationExecutor;

    private final ExecutorService threads = Executors.newFixedThreadPool(REQUESTS);
    private TransactionTemplate tx;
    private UUID familyId;
    private UUID mom;
    private UUID kid;

    @BeforeEach
    void setUp() {
        // 이 컨텍스트가 뜰 때 기동 따라잡기가 같은 풀에서 돌 수 있다 — 끝난 뒤에 잰다
        awaitNotificationPoolIdle();
        tx = new TransactionTemplate(transactionManager);
        familyId = rows.family("풀 경합");
        mom = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "은영");
        kid = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        threads.shutdownNow();
        threads.awaitTermination(10, TimeUnit.SECONDS);
        awaitNotificationPoolIdle();
        String profiles = "select id from profiles where family_id = ?";
        jdbc.update("delete from notifications where profile_id in (" + profiles + ")", familyId);
        jdbc.update("delete from cheers where family_id = ?", familyId);
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
    }

    /**
     * 요청 스레드마다 응원을 커밋한다(CheerService 와 같은 모양 — 같은 트랜잭션에서 저장하고 CheerSent 를 낸다). 커밋 뒤 알림이
     * 요청 스레드에서 두 번째 커넥션을 잡으면, 풀이 찬 채로 서로를 기다리다 연결 대기(1초)가 지나야 풀린다.
     */
    @Test
    @DisplayName("풀보다 많은 요청이 한꺼번에 커밋돼도 알림이 빠지지 않고, 요청이 연결 대기만큼 멈추지 않는다")
    void 풀보다_많은_요청이_한꺼번에_커밋돼도_알림이_빠지지_않는다() throws Exception {
        CyclicBarrier start = new CyclicBarrier(REQUESTS);
        List<Future<Long>> requests = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            requests.add(threads.submit(() -> {
                start.await();
                long began = System.nanoTime();
                tx.executeWithoutResult(status -> sendDone());
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
            }));
        }
        int failed = 0;
        long slowest = 0;
        for (Future<Long> request : requests) {
            try {
                slowest = Math.max(slowest, request.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                failed++;
            }
        }
        int made = awaitNotifications(mom, REQUESTS);
        log.info("요청 {}건 — 실패 {}건 · 가장 긴 요청 {}ms · 만든 알림 {}건", REQUESTS, failed, slowest, made);

        assertThat(new Outcome(failed, made, slowest < CONNECTION_TIMEOUT_MILLIS))
                .as("실패한 요청 · 만든 알림 · 모든 요청이 연결 대기(%dms)보다 빨랐나 — 가장 긴 요청 %dms", CONNECTION_TIMEOUT_MILLIS, slowest)
                .isEqualTo(new Outcome(0, REQUESTS, true));
    }

    /**
     * 같은 (받는 사람, 멱등 키)를 두 트랜잭션이 함께 넣는다 — 앞선 쪽이 커밋하기 전에 뒤 쪽이 넣는다. 「있나 보고 → 넣기」 는 둘 다
     * 「없음」 을 보고 늦게 커밋하는 쪽이 유니크 제약에 걸려 실패한다. 충돌을 DB 가 삼키면 둘 다 성공하고 한 건만 남는다.
     */
    @Test
    @DisplayName("같은 알림을 두 트랜잭션이 함께 넣어도 예외 없이 한 건만 남는다")
    void 같은_알림을_두_트랜잭션이_함께_넣어도_한_건() throws Exception {
        Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDate today = LocalDate.ofInstant(at, KST);
        Notification first = Notification.remeasure(mom, kid, "서준", today.minusDays(40), today, at);
        Notification second = Notification.remeasure(mom, kid, "서준", today.minusDays(40), today, at.plusSeconds(1));
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);

        Future<Boolean> early = threads.submit(() -> tx.execute(status -> {
            boolean made = notifications.insertIfAbsent(first);
            inserted.countDown();
            awaitQuietly(commit);
            return made;
        }));
        assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
        Future<Boolean> late = threads.submit(() -> tx.execute(status -> notifications.insertIfAbsent(second)));
        Thread.sleep(300); // 뒤 쪽이 넣기까지 가도록 둔다
        commit.countDown();

        List<Object> results = new ArrayList<>();
        for (Future<Boolean> it : List.of(early, late)) {
            try {
                results.add(it.get(10, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                results.add(e.getCause().getClass().getSimpleName());
            }
        }
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(count(mom)).isEqualTo(1);
    }

    @Test
    @DisplayName("스케줄러 스레드는 둘 이상 — 07:30 · 09:00 알림이 가족을 도는 동안 멈춘 편성 정리 · 리그 정산이 밀리지 않게")
    void 스케줄러_스레드는_둘_이상() {
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                .isGreaterThanOrEqualTo(2);
    }

    private void sendDone() {
        CheerSent cheer = new CheerSent(
                UUID.randomUUID(), familyId, kid, mom, CheerKind.DONE, null, "했어요", null, null, Instant.now());
        jdbc.update(
                """
                insert into cheers (id, family_id, from_profile_id, to_profile_id, kind, sticker_id, message, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)\
                """,
                cheer.cheerId(),
                familyId,
                kid,
                mom,
                cheer.kind().name(),
                null,
                cheer.message(),
                cheer.createdAt());
        events.publishEvent(cheer);
    }

    /** 알림은 커밋 뒤 따로 쓰므로 조금 늦게 생긴다 — 기다린 뒤의 수를 돌려준다(끝내 모자라면 모자란 수 그대로). */
    private int awaitNotifications(UUID profileId, int expected) {
        try {
            await().atMost(Duration.ofSeconds(5)).until(() -> count(profileId) >= expected);
        } catch (ConditionTimeoutException e) {
            log.warn("알림이 {}초 안에 {}건이 되지 않았다", 5, expected);
        }
        return count(profileId);
    }

    /** 알림 풀이 비동기 풀인지 보고, 돌거나 기다리는 일이 없어질 때까지 기다린다. */
    private void awaitNotificationPoolIdle() {
        assertThat(notificationExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) notificationExecutor;
        await().atMost(Duration.ofSeconds(30)).until(() -> pool.getActiveCount() == 0 && pool.getQueueSize() == 0);
    }

    private int count(UUID profileId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from notifications where profile_id = ?", Integer.class, profileId);
        return n == null ? 0 : n;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param failedRequests 커밋하지 못한 요청 수
     * @param notifications 만든 알림 수
     * @param allFast 모든 요청이 연결 대기보다 빨리 끝났나
     */
    private record Outcome(int failedRequests, int notifications, boolean allFast) {}
}
