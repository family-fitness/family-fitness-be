package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Duration;
import java.time.Instant;
import kr.ac.kookmin.familyfitness.identity.application.port.RefreshTokenRepository;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 발급 기록(refresh_tokens) 정리. refresh 할 때마다 행이 하나씩 늘므로 하루 한 번 지운다.
 * 지우는 행은 ① expires_at 이 만료 보관 기간(기본 0)보다 더 지난 행 ② revoked_at 이 폐기 보관 기간(기본 30일)보다 더 지난 행이다.
 *
 * <p>① 은 지워도 결과가 바뀌는 요청이 없다. 만료된 토큰은 {@code ServiceTokenIssuer.readRefreshToken} 이 먼저 거부해서
 * 이 표를 읽지 않는다. ② 는 폐기 보관 기간이 리프레시 토큰 수명({@code app.auth.jwt.refresh-ttl})보다 짧으면 안 된다.
 * 아직 만료 전인 폐기 토큰의 행이 지워지면, 그 토큰이 다시 와도 재사용으로 보지 못하고(묶음 폐기가 빠진다) 「발급 기록 없음」으로만
 * 거부한다. 그래서 더 짧게 설정하면 기동 때 멈춘다. 기본값(수명 30일 · 폐기 보관 30일)이면 ② 에 걸리는 행은 이미 ① 에도 걸린다.
 */
@Component
public class RefreshTokenSweeper {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RefreshTokenRepository tokens;
    private final IdentityClock clock;
    private final Duration expiredRetention;
    private final Duration revokedRetention;

    public RefreshTokenSweeper(
            RefreshTokenRepository tokens,
            IdentityClock clock,
            AppProperties props,
            @Value("${app.auth.refresh-token-cleanup.expired-retention:PT0S}") Duration expiredRetention,
            @Value("${app.auth.refresh-token-cleanup.revoked-retention:P30D}") Duration revokedRetention) {
        Duration refreshTtl = props.auth().jwt().refreshTtl();
        if (expiredRetention.isNegative()) {
            throw new IllegalStateException(
                    "app.auth.refresh-token-cleanup.expired-retention 은 0 이상이어야 합니다: " + expiredRetention);
        }
        if (revokedRetention.compareTo(refreshTtl) < 0) {
            throw new IllegalStateException("app.auth.refresh-token-cleanup.revoked-retention(" + revokedRetention
                    + ")은 app.auth.jwt.refresh-ttl(" + refreshTtl + ") 이상이어야 합니다. 더 짧으면 아직 유효한 폐기 토큰의 행이"
                    + " 지워져 재사용 감지가 빠집니다");
        }
        this.tokens = tokens;
        this.clock = clock;
        this.expiredRetention = expiredRetention;
        this.revokedRetention = revokedRetention;
    }

    /** 매일 한 번(기본 04:00, app.timezone 기준). 지운 행 수를 돌려준다. 스케줄러는 돌려준 값을 쓰지 않는다. */
    @Scheduled(cron = "${app.auth.refresh-token-cleanup.cron:0 0 4 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public int sweep() {
        Instant now = clock.now();
        long started = System.nanoTime();
        int deleted = tokens.deleteExpiredOrRevoked(now.minus(expiredRetention), now.minus(revokedRetention));
        long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        if (deleted > 0) {
            log.info(
                    "리프레시 토큰 기록 {}건을 지웠다(만료 뒤 {} · 폐기 뒤 {} 지난 행, {}ms)",
                    deleted,
                    expiredRetention,
                    revokedRetention,
                    tookMs);
        }
        return deleted;
    }
}
