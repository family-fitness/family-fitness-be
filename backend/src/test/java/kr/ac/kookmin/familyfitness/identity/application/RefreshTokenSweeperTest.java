package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** test 프로필의 H2(PostgreSQL 모드)에 행을 직접 넣고 정리 작업의 삭제 문장이 지우는 행을 본다. 설정은 기본값(0 · 30일)이다. */
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenSweeperTest {
    @Autowired
    private AuthService auth;

    @Autowired
    private RefreshTokenSweeper sweeper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("만료된 행과 폐기된 지 30일이 지난 행만 지운다 — 살아 있는 행과 폐기된 지 30일이 안 된 행은 남는다")
    void 만료된_행과_폐기된_지_30일이_지난_행만_지운다() {
        AuthResult login = auth.devLogin("sweep-" + UUID.randomUUID(), null, null);
        UUID userId = login.session().userId();
        Instant now = Instant.now();
        UUID active = insert(userId, now.plus(Duration.ofDays(1)), null);
        UUID expired = insert(userId, now.minus(Duration.ofMinutes(1)), null);
        // 기본 설정(수명 30일)에서는 생기지 않는 행이지만 폐기 조건만 따로 보려고 만료 전으로 둔다.
        UUID revokedLongAgo = insert(userId, now.plus(Duration.ofDays(1)), now.minus(Duration.ofDays(31)));
        UUID revokedRecently = insert(userId, now.plus(Duration.ofDays(1)), now.minus(Duration.ofDays(29)));

        assertThat(sweeper.sweep()).isGreaterThanOrEqualTo(2);

        assertThat(jdbc.queryForList("select jti from refresh_tokens where user_id = ?", UUID.class, userId))
                .containsExactlyInAnyOrder(login.tokens().refreshTokenId(), active, revokedRecently)
                .doesNotContain(expired, revokedLongAgo);
    }

    @Test
    @DisplayName("폐기 보관 기간이 리프레시 토큰 수명(기본 30일)보다 짧으면 만들 때 멈춘다")
    void 폐기_보관_기간이_토큰_수명보다_짧으면_멈춘다() {
        IdentityClock clock = new IdentityClock(Clock.systemUTC(), ZoneId.of("Asia/Seoul"));

        assertThatThrownBy(() -> new RefreshTokenSweeper(
                        new InMemoryRefreshTokenRepository(),
                        clock,
                        new AppProperties(),
                        Duration.ZERO,
                        Duration.ofDays(29)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh-ttl");
    }

    private UUID insert(UUID userId, Instant expiresAt, @Nullable Instant revokedAt) {
        UUID jti = UUID.randomUUID();
        jdbc.update(
                "insert into refresh_tokens (jti, user_id, family_id, expires_at, revoked_at, created_at)"
                        + " values (?, ?, ?, ?, ?, ?)",
                jti,
                userId,
                UUID.randomUUID(),
                utc(expiresAt),
                revokedAt == null ? null : utc(revokedAt),
                utc(expiresAt.minus(Duration.ofDays(30))));
        return jti;
    }

    private static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
