package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyReviewLoginsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewLoginLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T01:00:00Z"));
    private final ReviewLoginLimiter limiter = new ReviewLoginLimiter(clock.identityClock());

    @Test
    @DisplayName("한 IP 가 한 시간에 30번까지 되고 31번째는 막힌다. 다른 IP 는 따로 센다")
    void 한_IP_는_한_시간에_30번까지() {
        for (int i = 0; i < ReviewLoginLimiter.MAX_LOGINS; i++) limiter.acquire("1.1.1.1");

        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(TooManyReviewLoginsException.class);
        assertThatCode(() -> limiter.acquire("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("막힌 요청은 세지 않는다 — 처음 통과한 때로부터 한 시간이 지나면 다시 된다")
    void 한_시간이_지나면_풀린다() {
        Instant start = clock.instant();
        for (int i = 0; i < ReviewLoginLimiter.MAX_LOGINS; i++) limiter.acquire("1.1.1.1");
        clock.setInstant(start.plusSeconds(3599));
        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(TooManyReviewLoginsException.class);

        clock.setInstant(start.plus(ReviewLoginLimiter.WINDOW));

        assertThatCode(() -> limiter.acquire("1.1.1.1")).doesNotThrowAnyException();
    }
}
