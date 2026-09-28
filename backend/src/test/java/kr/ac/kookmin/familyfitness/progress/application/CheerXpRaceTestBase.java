package kr.ac.kookmin.familyfitness.progress.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.ac.kookmin.familyfitness.coaching.application.AppTime;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.application.CheerService;
import kr.ac.kookmin.familyfitness.identity.application.CreatedFamily;
import kr.ac.kookmin.familyfitness.identity.application.FamilyService;
import kr.ac.kookmin.familyfitness.identity.application.SendCheerCommand;
import kr.ac.kookmin.familyfitness.identity.application.UserRegistrationService;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyThankedException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 두 보호자의 응원이 같은 순간 겹칠 때 — 실제 DB 에 커밋하며 본다(QA SA-12). H2 판과 PostgreSQL 판이 이 클래스를 잇는다.
 *
 * <p>응원 저장과 경험치 · 업적 적립은 한 트랜잭션이다(ProgressEventListener). 엄마의 응원을 커밋 바로 앞에서 멈춰 세우고
 * ({@link Pause}) 그동안 아빠의 응원을 보낸다. 멈춘 동안 엄마 트랜잭션이 넣은 행과 잠금은 그대로다.
 * 모든 행을 커밋하므로 끝나면 이 가족의 행을 지운다.
 */
abstract class CheerXpRaceTestBase {
    /** 멈춘 요청 뒤에 보낸 요청이 잠금 앞까지 가도록 잠깐 둔다. H2 잠금 대기 기본값(2초)보다 짧아야 한다. */
    private static final long SETTLE_MILLIS = 300;

    @Autowired
    UserRegistrationService registration;

    @Autowired
    FamilyService familyService;

    @Autowired
    FamilyRepository familyRepository;

    @Autowired
    CheerService cheers;

    @Autowired
    MissionRepository missions;

    @Autowired
    AppTime time;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    UUID momUser;
    UUID dadUser;
    UUID familyId;
    UUID momId;
    UUID dadId;
    UUID kidId;
    UUID missionId;

    /** 엄마(가족을 만든 사람) · 아빠(계정 붙음) · 계정 없는 아이, 오늘 하루짜리 아이 미션 하나. */
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        momUser = registration
                .registerOrGet(User.PROVIDER_DEV, "race-mom-" + suffix, null)
                .id();
        dadUser = registration
                .registerOrGet(User.PROVIDER_DEV, "race-dad-" + suffix, null)
                .id();
        CreatedFamily family = familyService.createFamily(momUser, "응원 경합", "엄마", LocalDate.of(1988, 3, 1), Sex.F);
        familyId = family.familyId();
        momId = family.ownerProfile().profileId();
        dadId = familyService
                .addMember(
                        momUser, familyId, "아빠", LocalDate.of(1986, 1, 1), Sex.M, ProfileRole.PARENT, null, null, null)
                .profileId();
        tx().executeWithoutResult(status -> familyRepository.attachUserIfUnclaimed(dadId, dadUser, Instant.now()));
        kidId = familyService
                .addMember(
                        momUser,
                        familyId,
                        "서준",
                        LocalDate.of(2016, 5, 1),
                        Sex.M,
                        ProfileRole.CHILD,
                        null,
                        null,
                        new GuardianConsent(true, true))
                .profileId();

        missionId = UUID.randomUUID();
        LocalDate today = time.today();
        tx().executeWithoutResult(status -> missions.save(Mission.manual(
                missionId,
                familyId,
                "거북이 스트레칭",
                TargetMetric.TIMER_MINUTES,
                1,
                null,
                today,
                today,
                List.of(kidId),
                List.of(new MissionSession(1, SessionPhase.MAIN, "거북이 스트레칭", FitnessFactor.FLEXIBILITY, 1, null)),
                momId,
                Instant.now())));
    }

    @AfterEach
    void tearDown() {
        for (String table : List.of("mission_session_completions", "mission_participants", "mission_sessions")) {
            jdbc.update("delete from " + table + " where mission_id = ?", missionId);
        }
        jdbc.update("delete from missions where id = ?", missionId);
        jdbc.update("delete from notifications where profile_id in (?, ?, ?)", momId, dadId, kidId);
        // 고마워요는 칭찬 스티커를 가리키므로(fk_cheers_reply_to) 먼저 지운다
        jdbc.update("delete from cheers where family_id = ? and reply_to_cheer_id is not null", familyId);
        jdbc.update("delete from cheers where family_id = ?", familyId);
        for (String table : List.of("progress_xp_events", "progress_achievements", "consent_events")) {
            jdbc.update("delete from " + table + " where profile_id in (?, ?, ?)", momId, dadId, kidId);
        }
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
        jdbc.update("delete from users where id in (?, ?)", momUser, dadUser);
    }

    @Test
    @DisplayName("두 보호자가 같은 아이 · 같은 운동에 칭찬 스티커를 동시에 붙여도 응원은 둘 다 저장되고, +10 과 「첫 스티커」 는 한 번")
    void 두_보호자의_칭찬_스티커가_겹쳐도_응원은_둘_다_저장된다() throws InterruptedException {
        Pause pause = new Pause();

        Race<Cheer, Cheer> race = race(
                pause,
                () -> tx().execute(status -> {
                    Cheer sent = cheers.cheer(momUser, familyId, praise(momId, "star"));
                    pause.hold();
                    return sent;
                }),
                () -> tx().execute(status -> cheers.cheer(dadUser, familyId, praise(dadId, "crown"))));

        assertThat(resultOf(race.first())).isNotNull();
        assertThat(resultOf(race.second())).isNotNull();
        assertThat(count("select count(*) from cheers where family_id = ? and kind = 'PRAISE'", familyId))
                .isEqualTo(2);
        assertThat(count("select count(*) from progress_xp_events where profile_id = ? and kind = 'STICKER'", kidId))
                .isOne();
        assertThat(count("select coalesce(sum(amount), 0) from progress_xp_events where profile_id = ?", kidId))
                .isEqualTo(10);
        assertThat(count(
                        "select count(*) from progress_achievements where profile_id = ? and code = 'FIRST_STICKER'",
                        kidId))
                .isOne();
    }

    @Test
    @DisplayName("같은 스티커에 고마워요 두 번이 동시에 오면 늦은 쪽은 409 ALREADY_THANKED — 고마워요는 한 번만 남는다")
    void 같은_스티커에_고마워요가_겹치면_늦은_쪽은_ALREADY_THANKED() throws InterruptedException {
        UUID praiseId = tx().execute(status -> cheers.cheer(momUser, familyId, praise(momId, "star")))
                .id();
        Pause pause = new Pause();

        Race<Cheer, Cheer> race = race(
                pause,
                () -> tx().execute(status -> {
                    Cheer sent = cheers.cheer(momUser, familyId, thanks(praiseId));
                    pause.hold();
                    return sent;
                }),
                () -> tx().execute(status -> cheers.cheer(dadUser, familyId, thanks(praiseId))));

        assertThat(resultOf(race.first())).isNotNull();
        assertThat(failureOf(race.second())).isInstanceOf(AlreadyThankedException.class);
        assertThat(count("select count(*) from cheers where reply_to_cheer_id = ?", praiseId))
                .isOne();
    }

    private SendCheerCommand praise(UUID fromProfileId, String stickerId) {
        return new SendCheerCommand(fromProfileId, kidId, CheerKind.PRAISE, null, stickerId, missionId, null);
    }

    /** 계정 없는 아이 이름으로 엄마에게 보내는 고마워요. 보호자 둘 다 아이를 대신할 수 있다. */
    private SendCheerCommand thanks(UUID praiseId) {
        return new SendCheerCommand(kidId, momId, CheerKind.THANKS, "고마워요", "heart", null, praiseId);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private int count(String sql, Object arg) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arg);
        return value == null ? 0 : value;
    }

    record Race<A, B>(Future<A> first, Future<B> second) {}

    /**
     * {@code first} 를 돌려 {@code pause} 에서 멈추게 한 뒤 {@code second} 를 돌린다. {@code second} 가 잠금 앞까지 가도록 잠깐 두고
     * {@code first} 를 풀어 준다. 둘 다 끝난 뒤 돌려준다.
     */
    static <A, B> Race<A, B> race(Pause pause, Callable<A> first, Callable<B> second) throws InterruptedException {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<A> firstDone = pool.submit(first);
            pause.awaitReached();
            Future<B> secondDone = pool.submit(second);
            try {
                secondDone.get(SETTLE_MILLIS, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | TimeoutException e) {
                // 잠금을 기다리는 중이거나 이미 끝났다. 어느 쪽이든 결과는 아래 호출한 쪽이 Future 로 본다.
            } finally {
                pause.release();
            }
            return new Race<>(firstDone, secondDone);
        }
    }

    static <T> @Nullable T resultOf(Future<T> future) {
        if (future.state() == Future.State.FAILED) throw new AssertionError("성공해야 했다", future.exceptionNow());
        return future.resultNow();
    }

    static Throwable failureOf(Future<?> future) {
        if (future.state() != Future.State.FAILED) throw new AssertionError("실패해야 했다: " + future.state());
        return future.exceptionNow();
    }

    /** 한 요청을 정한 자리에서 멈춰 세운다. 멈춘 동안 그 요청의 트랜잭션 · 잠금은 그대로다. */
    static final class Pause {
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        void hold() {
            reached.countDown();
            await(released, "풀어 주지 않았습니다");
        }

        void awaitReached() {
            await(reached, "멈출 자리까지 오지 않았습니다");
        }

        void release() {
            released.countDown();
        }

        private static void await(CountDownLatch latch, String timeoutMessage) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(timeoutMessage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
