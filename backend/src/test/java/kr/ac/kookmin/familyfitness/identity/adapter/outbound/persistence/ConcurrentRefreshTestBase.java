package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import kr.ac.kookmin.familyfitness.identity.application.AuthResult;
import kr.ac.kookmin.familyfitness.identity.application.AuthService;
import kr.ac.kookmin.familyfitness.identity.application.port.RefreshTokenRepository;
import kr.ac.kookmin.familyfitness.identity.domain.RefreshToken;
import kr.ac.kookmin.familyfitness.shared.security.InvalidRefreshTokenException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 리프레시 토큰으로 온 두 refresh 가 실제 DB 에서 겹칠 때 한쪽만 회전하는지 본다. H2 판과 PostgreSQL 판이 이 클래스를 잇는다.
 *
 * <p>두 스레드가 각자 트랜잭션으로 {@link AuthService#refresh} 를 부른다. {@link RotateBarrier} 가 두 요청을 조건부
 * UPDATE(rotate) 직전에 붙잡아 두므로, 둘 다 발급 기록을 읽어 「아직 살아 있음」을 본 뒤에야 UPDATE 를 낸다. 늦은 UPDATE 는
 * 앞선 트랜잭션의 행 잠금이 풀릴 때까지 기다렸다가 WHERE({@code revoked_at is null})를 다시 평가해 0행을 받아야 한다
 * (READ COMMITTED). 격리 수준이나 UPDATE 조건이 바뀌어 두 요청이 다 통과하거나 늦은 쪽이 다른 오류로 끝나면 여기서 깨진다.
 */
abstract class ConcurrentRefreshTestBase {
    @Autowired
    private AuthService auth;

    @Autowired
    private RotateBarrier barrier;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("같은 리프레시 토큰으로 두 요청이 동시에 오면 한쪽만 새 토큰을 받고, 늦은 쪽이 그 묶음을 폐기한다")
    void 같은_리프레시_토큰으로_두_요청이_동시에_오면_한쪽만_새_토큰을_받고_늦은_쪽이_그_묶음을_폐기한다() {
        AuthResult login = auth.devLogin("race-" + UUID.randomUUID(), null, null);
        String token = login.tokens().refreshToken();

        List<Future<AuthResult>> calls;
        barrier.arm(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            calls = List.of(pool.submit(() -> auth.refresh(token)), pool.submit(() -> auth.refresh(token)));
        } finally {
            barrier.disarm();
        }

        assertThat(calls)
                .extracting(Future::state)
                .containsExactlyInAnyOrder(Future.State.SUCCESS, Future.State.FAILED);
        assertThat(only(calls, Future.State.FAILED).exceptionNow()).isInstanceOf(InvalidRefreshTokenException.class);
        AuthResult winner = only(calls, Future.State.SUCCESS).resultNow();

        UUID oldJti = login.tokens().refreshTokenId();
        UUID familyId = jdbc.queryForObject("select family_id from refresh_tokens where jti = ?", UUID.class, oldJti);
        // 한 번만 회전했다: 옛 토큰의 다음 토큰은 이긴 쪽이 받은 토큰이다.
        assertThat(jdbc.queryForObject("select replaced_by from refresh_tokens where jti = ?", UUID.class, oldJti))
                .isEqualTo(winner.tokens().refreshTokenId());
        // 묶음에는 옛 토큰과 이긴 쪽 토큰 두 행만 있고, 늦은 쪽이 폐기한 것이 커밋돼 살아 있는 행이 없다.
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ?", Integer.class, familyId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_tokens where family_id = ? and revoked_at is null",
                        Integer.class,
                        familyId))
                .isZero();
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> auth.refresh(winner.tokens().refreshToken()));
    }

    private static Future<AuthResult> only(List<Future<AuthResult>> calls, Future.State state) {
        return calls.stream().filter(it -> it.state() == state).findFirst().orElseThrow();
    }

    /** 진짜 저장소(RefreshTokenRepositoryAdapter)를 감싸, arm 했을 때만 rotate 직전에 요청들을 서로 기다리게 한다. */
    static final class RotateBarrier implements RefreshTokenRepository {
        private final RefreshTokenRepository delegate;
        private final AtomicReference<@Nullable CyclicBarrier> barrier = new AtomicReference<>();

        RotateBarrier(RefreshTokenRepository delegate) {
            this.delegate = delegate;
        }

        void arm(int parties) {
            barrier.set(new CyclicBarrier(parties));
        }

        void disarm() {
            barrier.set(null);
        }

        @Override
        public void add(RefreshToken token) {
            delegate.add(token);
        }

        @Override
        public @Nullable RefreshToken findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public boolean rotate(UUID id, UUID replacedBy, Instant at) {
            @Nullable CyclicBarrier current = barrier.get();
            if (current != null) await(current);
            return delegate.rotate(id, replacedBy, at);
        }

        @Override
        public int revokeFamily(UUID familyId, Instant at) {
            return delegate.revokeFamily(familyId, at);
        }

        @Override
        public int deleteExpiredOrRevoked(Instant expiredBefore, Instant revokedBefore) {
            return delegate.deleteExpiredOrRevoked(expiredBefore, revokedBefore);
        }

        private static void await(CyclicBarrier current) {
            try {
                current.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("다른 요청이 rotate 앞까지 오지 않았습니다", e);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RotateBarrierConfig {
        @Bean
        @Primary
        RotateBarrier rotateBarrier(@Qualifier("refreshTokenRepositoryAdapter") RefreshTokenRepository adapter) {
            return new RotateBarrier(adapter);
        }
    }
}
