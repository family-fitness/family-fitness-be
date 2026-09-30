package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.identity.application.port.AvailabilityRepository;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability.RawSlot;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 프로필의 한 주를 두 요청이 겹쳐 바꿀 때 두 요청의 칸이 섞이지 않는지 본다. test 프로필의 H2(READ COMMITTED)에서 돈다.
 *
 * <p>앞 요청이 월요일 칸을 넣고 커밋하기 전에 뒤 요청(화요일)이 들어온다. 앞 요청은 뒤 요청이 끝났다는 신호를 짧게 기다린 뒤 커밋한다.
 * 프로필 행을 잠그지 않으면 뒤 요청이 앞 요청의 월요일 칸을 보지 못한 채 지우기 · 넣기를 끝내고 먼저 커밋해 월 · 화가 둘 다 남는다.
 * 잠그면 뒤 요청은 앞 요청이 커밋할 때까지 기다렸다가 월요일 칸까지 지우므로 화요일만 남는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AvailabilityConcurrentReplaceTest {
    private static final long HOLD_MILLIS = 300;

    @Autowired
    AvailabilityRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    private UUID familyId;
    private UUID momId;
    private UUID kidId;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from profile_availability_slots where profile_id in (?, ?)", kidId, momId);
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
    }

    private static WeeklyAvailability week(String day) {
        return WeeklyAvailability.parse(List.of(new RawSlot(day, "19:00", BigDecimal.valueOf(20))));
    }

    @Test
    @DisplayName("두 요청이 겹쳐 다른 요일로 바꾸면 나중에 커밋한 요청의 한 주만 남는다 — 두 요청의 칸이 합쳐지지 않는다")
    void laterReplaceWins() throws Exception {
        CountDownLatch firstWrote = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        Instant now = Instant.parse("2026-09-29T00:00:00Z");

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                repository.replace(kidId, week("MON"), momId, now);
                firstWrote.countDown();
                awaitQuietly(secondDone, HOLD_MILLIS);
            }));
            Future<?> second = pool.submit(() -> {
                awaitQuietly(firstWrote, 5_000);
                tx.executeWithoutResult(status -> repository.replace(kidId, week("TUE"), momId, now));
                secondDone.countDown();
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(repository.findByProfileId(kidId))
                .containsExactly(new AvailabilitySlot(DayOfWeek.TUESDAY, LocalTime.of(19, 0), 20));
    }

    private static void awaitQuietly(CountDownLatch latch, long millis) {
        try {
            latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
