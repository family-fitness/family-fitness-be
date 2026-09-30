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
            limiter.remember(ip, userId);
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
    @DisplayName("모두 합쳐 한 시간에 300개를 만들면 그다음은 429 가 아니라, 그 IP 가 이 한 시간에 만든 가장 최근 계정으로 들인다")
    void 모두_합친_한도가_차면_그_IP_가_만든_계정으로_들인다() {
        Instant start = clock.instant();
        List<UUID> made = new ArrayList<>();
        for (int i = 0; i < ReviewLoginLimiter.MAX_TOTAL; i++) {
            assertThat(loginAndRemember("10.0." + (i / 200) + "." + (i % 200), made)
                            .reuse())
                    .isNull();
        }
        // 10.0.0.5 가 한 번 더 만들어 두었다고 친다(한도 전에 만든 계정 둘 가운데 뒤의 것을 받는다)
        UUID later = UUID.randomUUID();
        limiter.remember("10.0.0.5", later);

        assertThat(limiter.acquire("10.0.0.5").reuse()).isEqualTo(later);
        assertThat(limiter.acquire("10.0.1.7").reuse()).isEqualTo(made.get(207));

        // 나눠 줄 때도 IP 마다 30번은 그대로 센다
        for (int i = 2; i < ReviewLoginLimiter.MAX_LOGINS; i++) limiter.acquire("10.0.0.5");
        assertThatThrownBy(() -> limiter.acquire("10.0.0.5")).isInstanceOf(TooManyReviewLoginsException.class);

        clock.setInstant(start.plus(ReviewLoginLimiter.WINDOW));
        assertThat(limiter.acquire("10.0.1.7").reuse()).isNull();
    }

    @Test
    @DisplayName("모두 합친 한도가 찬 뒤 만든 계정이 없는 IP 는 새 계정을 하나 받고, 그 뒤로는 그 계정으로 들어온다 — 다른 IP 가 만든 계정은 받지 않는다")
    void 한도가_찬_뒤_새_IP_는_새_계정_하나() {
        List<UUID> attacker = new ArrayList<>();
        for (int i = 0; i < ReviewLoginLimiter.MAX_TOTAL; i++) {
            loginAndRemember("10.0." + (i / 200) + "." + (i % 200), attacker);
        }

        List<UUID> reviewer = new ArrayList<>();
        assertThat(loginAndRemember("9.9.9.9", reviewer).reuse()).isNull();
        assertThat(reviewer).hasSize(1);
        for (int i = 0; i < 5; i++) {
            assertThat(loginAndRemember("9.9.9.9", reviewer).reuse()).isEqualTo(reviewer.getFirst());
        }
        assertThat(reviewer).hasSize(1).doesNotContainAnyElementsOf(attacker);

        // 계정을 적기 전에 실패했으면(만들다 되돌림) 나눠 줄 것이 없어 다시 새 계정이다 — 429 가 아니다
        assertThat(limiter.acquire("8.8.8.8").reuse()).isNull();
        assertThat(limiter.acquire("8.8.8.8").reuse()).isNull();
    }

    @Test
    @DisplayName("한 시간이 지난 계정은 다시 주지 않는다 — 다시 주는 것은 최근 한 시간에 그 IP 가 만든 계정뿐이다")
    void 한_시간이_지난_계정은_다시_주지_않는다() {
        Instant start = clock.instant();
        UUID old = UUID.randomUUID();
        limiter.acquire("7.7.7.7");
        limiter.remember("7.7.7.7", old);

        clock.setInstant(start.plus(ReviewLoginLimiter.WINDOW).plusSeconds(1));
        List<UUID> made = new ArrayList<>();
        for (int i = 0; i < ReviewLoginLimiter.MAX_TOTAL; i++) {
            loginAndRemember("10.0." + (i / 200) + "." + (i % 200), made);
        }

        assertThat(limiter.acquire("7.7.7.7").reuse()).isNull();
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
    @DisplayName("IP 이름: IPv4 는 그대로, IPv4 에 대응된 IPv6 는 IPv4 로, IPv6 는 /56 대역을 표준 표기로, IP 가 아니면 받은 글자 그대로")
    void IP_이름() {
        assertThat(ReviewLoginLimiter.keyOf("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ReviewLoginLimiter.keyOf("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ReviewLoginLimiter.keyOf("2001:db8::1"))
                .isEqualTo(ReviewLoginLimiter.keyOf("2001:0db8:0:ff:ffff::9"))
                .isEqualTo("2001:db8::/56")
                .isNotEqualTo(ReviewLoginLimiter.keyOf("2001:db8:0:100::1"));
        assertThat(ReviewLoginLimiter.keyOf("unknown")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("로그에 남기는 IP 는 끝자리를 가린다 — IPv4 는 마지막 옥텟을 *, IPv6 는 /56 뒤를 지운 대역, IP 가 아니면 그 사실만")
    void 로그에_남기는_IP_는_끝자리를_가린다() {
        assertThat(ReviewLoginLimiter.maskedForLog("203.0.113.77")).isEqualTo("203.0.113.*");
        assertThat(ReviewLoginLimiter.maskedForLog("::ffff:203.0.113.77")).isEqualTo("203.0.113.*");
        assertThat(ReviewLoginLimiter.maskedForLog("2001:db8:abcd:12ab:1:2:3:4"))
                .isEqualTo("2001:db8:abcd:1200::/56");
        assertThat(ReviewLoginLimiter.maskedForLog("unknown")).isEqualTo("(IP 아님)");
    }

    @Test
    @DisplayName("IPv6 /56 대역은 RFC 5952 표준 표기로 쓴다 — 배포 점검에서 휴대폰 공인 IP 와 로그 줄을 눈으로 맞춰 본다")
    void IPv6_대역은_표준_표기로_쓴다() {
        // 예전에는 앞 7바이트를 16진수로 붙여 「20010db8abcd12::/56」 처럼 IPv6 표기가 아닌 글자가 찍혔다
        assertThat(ReviewLoginLimiter.keyOf("2001:db8:abcd:12ab::1")).isEqualTo("2001:db8:abcd:1200::/56");
        assertThat(ReviewLoginLimiter.keyOf("2001:DB8:ABCD:12FF:1:2:3:4")).isEqualTo("2001:db8:abcd:1200::/56");
        assertThat(ReviewLoginLimiter.keyOf("::1")).isEqualTo("::/56");
        assertThat(ReviewLoginLimiter.keyOf("fe80::1")).isEqualTo("fe80::/56");
        // 가운데 0 이 이어지면 가장 긴 0 묶음 하나만 줄인다
        assertThat(ReviewLoginLimiter.keyOf("2001:0:0:1200::1")).isEqualTo("2001:0:0:1200::/56");
    }

    @Test
    @DisplayName("막힌 요청은 세지 않는다 — 처음 통과하고 한 시간이 지나면 다시 된다")
    void 한_시간이_지나면_풀린다() {
        Instant start = clock.instant();
        for (int i = 0; i < ReviewLoginLimiter.MAX_LOGINS; i++) limiter.acquire("1.1.1.1");
        clock.setInstant(start.plusSeconds(3599));
        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(TooManyReviewLoginsException.class);

        clock.setInstant(start.plus(ReviewLoginLimiter.WINDOW));

        assertThatCode(() -> limiter.acquire("1.1.1.1")).doesNotThrowAnyException();
    }
}
