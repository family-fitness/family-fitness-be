package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("IP 가 모두 달라도 한 시간에 모두 합쳐 300번을 넘기면 막힌다 — IP 를 바꿔 가며 불러도 계정이 끝없이 쌓이지 않는다")
    void 모두_합쳐_한_시간에_300번까지() {
        Instant start = clock.instant();
        for (int i = 0; i < ReviewLoginLimiter.MAX_TOTAL; i++) {
            limiter.acquire("10.0." + (i / 200) + "." + (i % 200));
        }

        assertThatThrownBy(() -> limiter.acquire("9.9.9.9")).isInstanceOf(TooManyReviewLoginsException.class);

        clock.setInstant(start.plus(ReviewLoginLimiter.WINDOW));
        assertThatCode(() -> limiter.acquire("9.9.9.9")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IPv6 는 앞 64비트가 같으면 한 IP 로 센다 — 뒤 64비트만 바꿔 한도를 비켜 가지 못한다")
    void IPv6_는_64비트_대역으로_센다() {
        for (int i = 0; i < ReviewLoginLimiter.MAX_LOGINS; i++) {
            limiter.acquire("2001:db8:1:2:" + Integer.toHexString(i + 1) + "::1");
        }

        assertThatThrownBy(() -> limiter.acquire("2001:db8:1:2:ffff:ffff:ffff:ffff"))
                .isInstanceOf(TooManyReviewLoginsException.class);
        assertThatCode(() -> limiter.acquire("2001:db8:1:3::1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IP 이름: IPv4 는 그대로, IPv4 에 대응된 IPv6 는 IPv4 로, IPv6 는 /64 대역, IP 가 아니면 받은 글자 그대로")
    void IP_이름() {
        assertThat(ReviewLoginLimiter.keyOf("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ReviewLoginLimiter.keyOf("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ReviewLoginLimiter.keyOf("2001:db8::1"))
                .isEqualTo(ReviewLoginLimiter.keyOf("2001:0db8:0:0:ffff::9"));
        assertThat(ReviewLoginLimiter.keyOf("unknown")).isEqualTo("unknown");
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
