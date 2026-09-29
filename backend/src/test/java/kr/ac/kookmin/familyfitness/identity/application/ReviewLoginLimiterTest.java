package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyReviewLoginsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewLoginLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T01:00:00Z"));
    private final ReviewLoginLimiter limiter =
            new ReviewLoginLimiter(clock.identityClock(), ReviewLoginLimiter.MAX_TOTAL);

    /** 새 계정을 만들라는 답이면 만든 셈 치고 그 계정을 적어 둔다(ReviewLoginService 가 커밋 뒤에 하는 일). */
    private ReviewLoginLimiter.Admission loginAndRemember(String ip, List<UUID> made) {
        ReviewLoginLimiter.Admission admission = limiter.acquire(ip);
        if (admission.reuse() == null) {
            UUID userId = UUID.randomUUID();
            limiter.remember(userId);
            made.add(userId);
        }
        return admission;
    }

    @Test
    @DisplayName("한 IP 가 한 시간에 30번까지 되고 31번째는 막힌다. 다른 IP 는 따로 센다")
    void 한_IP_는_한_시간에_30번까지() {
        for (int i = 0; i < ReviewLoginLimiter.MAX_LOGINS; i++) limiter.acquire("1.1.1.1");

        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(TooManyReviewLoginsException.class);
        assertThatCode(() -> limiter.acquire("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("모두 합쳐 한 시간에 300개를 만들면 그다음은 429 가 아니라 그 한 시간에 만든 심사용 계정 가운데 하나를 준다 — 계정은 더 생기지 않고 심사위원은 들어온다")
    void 모두_합친_한도가_차면_최근_계정을_나눠_준다() {
        Instant start = clock.instant();
        List<UUID> made = new ArrayList<>();
        for (int i = 0; i < ReviewLoginLimiter.MAX_TOTAL; i++) {
            assertThat(loginAndRemember("10.0." + (i / 200) + "." + (i % 200), made)
                            .reuse())
                    .isNull();
        }

        for (int i = 0; i < 5; i++) {
            assertThat(loginAndRemember("9.9.9." + i, made).reuse()).isIn(made);
        }
        assertThat(made).hasSize(ReviewLoginLimiter.MAX_TOTAL);

        // 나눠 줄 때도 IP 마다 30번은 그대로 센다
        for (int i = 0; i < ReviewLoginLimiter.MAX_LOGINS; i++) limiter.acquire("8.8.8.8");
        assertThatThrownBy(() -> limiter.acquire("8.8.8.8")).isInstanceOf(TooManyReviewLoginsException.class);

        clock.setInstant(start.plus(ReviewLoginLimiter.WINDOW));
        assertThat(limiter.acquire("9.9.9.9").reuse()).isNull();
    }

    @Test
    @DisplayName("모두 합친 한도가 찼는데 나눠 줄 계정이 없으면(만들다 실패해 적힌 계정이 없음) 429 다")
    void 나눠_줄_계정이_없으면_429() {
        for (int i = 0; i < ReviewLoginLimiter.MAX_TOTAL; i++) {
            limiter.acquire("10.0." + (i / 200) + "." + (i % 200));
        }

        assertThatThrownBy(() -> limiter.acquire("9.9.9.9")).isInstanceOf(TooManyReviewLoginsException.class);
    }

    @Test
    @DisplayName("한 시간이 지난 계정은 나눠 주지 않는다 — 나눠 주는 것은 최근 한 시간에 만든 계정뿐이다")
    void 한_시간이_지난_계정은_나눠_주지_않는다() {
        Instant start = clock.instant();
        UUID old = UUID.randomUUID();
        limiter.acquire("7.7.7.7");
        limiter.remember(old);

        clock.setInstant(start.plus(ReviewLoginLimiter.WINDOW).plusSeconds(1));
        List<UUID> made = new ArrayList<>();
        for (int i = 0; i < ReviewLoginLimiter.MAX_TOTAL; i++) {
            loginAndRemember("10.0." + (i / 200) + "." + (i % 200), made);
        }

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.acquire("9.9.9." + i).reuse()).isIn(made).isNotEqualTo(old);
        }
    }

    @Test
    @DisplayName("IPv6 는 앞 56비트가 같으면 한 IP 로 센다 — 집 한 곳이 받는 /56 안에서 /64 대역을 바꿔 가며 한도를 비켜 가지 못한다")
    void IPv6_는_56비트_대역으로_센다() {
        for (int i = 0; i < ReviewLoginLimiter.MAX_LOGINS; i++) {
            // 2001:db8:1:200::/56 안의 서로 다른 /64 대역
            limiter.acquire("2001:db8:1:2" + String.format("%02x", i) + ":" + Integer.toHexString(i + 1) + "::1");
        }

        assertThatThrownBy(() -> limiter.acquire("2001:db8:1:2ff:ffff:ffff:ffff:ffff"))
                .isInstanceOf(TooManyReviewLoginsException.class);
        assertThatCode(() -> limiter.acquire("2001:db8:1:300::1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IP 이름: IPv4 는 그대로, IPv4 에 대응된 IPv6 는 IPv4 로, IPv6 는 /56 대역, IP 가 아니면 받은 글자 그대로")
    void IP_이름() {
        assertThat(ReviewLoginLimiter.keyOf("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ReviewLoginLimiter.keyOf("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ReviewLoginLimiter.keyOf("2001:db8::1"))
                .isEqualTo(ReviewLoginLimiter.keyOf("2001:0db8:0:ff:ffff::9"))
                .isEqualTo("20010db8000000::/56")
                .isNotEqualTo(ReviewLoginLimiter.keyOf("2001:db8:0:100::1"));
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
