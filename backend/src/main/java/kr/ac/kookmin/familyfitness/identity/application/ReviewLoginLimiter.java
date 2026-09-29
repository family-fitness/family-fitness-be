package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyReviewLoginsException;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정 로그인을 IP 마다 센다. 최근 {@link #WINDOW} 안에 {@link #MAX_LOGINS} 번 만들었으면 다음 요청은 계정을 만들기 전에
 * 429 TOO_MANY 다. 부를 때마다 계정 하나 · 가족 하나 · 프로필 넷 · 측정 둘이 생기니, 누가 반복해서 부르면 DB 가 끝없이 찬다.
 *
 * <p>셈은 {@link ClaimAttemptLimiter} 와 같은 sliding window log 다. 통과한 요청만 센다(막힌 요청은 세지 않는다) — 그래서
 * 마지막으로 통과한 뒤 창이 지나면 풀린다. 세는 것은 통과를 정하는 그 순간이라, 같은 IP 가 동시에 여러 번 불러도 한도를 넘지 않는다.
 *
 * <p>저장은 이 프로세스의 메모리다. 서버가 한 대라 이것으로 된다. 재시작하면 셈이 비지만 한 IP 가 더 만들 수 있는 것은
 * 한 번에 {@link #MAX_LOGINS} 개다. IP 는 서블릿의 remoteAddr 이다. 역방향 프록시 뒤에 두면 모든 요청이 프록시 IP 로 보여
 * 심사위원 전체가 한도 하나를 나눠 쓰게 되므로, 그때는 {@code SERVER_FORWARD_HEADERS_STRATEGY=native} 로 X-Forwarded-For 를
 * 읽게 한다(README).
 */
@Component
public class ReviewLoginLimiter {
    static final int MAX_LOGINS = 30;
    static final Duration WINDOW = Duration.ofHours(1);

    private final IdentityClock clock;
    private final ConcurrentHashMap<String, List<Instant>> logins = new ConcurrentHashMap<>();
    private volatile Instant lastSweep = Instant.EPOCH;

    public ReviewLoginLimiter(IdentityClock clock) {
        this.clock = clock;
    }

    /** 한도 안이면 이번 요청을 세고 돌아온다. 한도에 닿았으면 세지 않고 {@link TooManyReviewLoginsException}. */
    public void acquire(String clientIp) {
        Instant now = clock.now();
        logins.compute(clientIp, (key, previous) -> {
            List<Instant> kept =
                    previous == null ? List.of() : recentOf(previous, now).toList();
            if (kept.size() >= MAX_LOGINS) throw new TooManyReviewLoginsException();
            return Stream.concat(kept.stream(), Stream.of(now)).toList();
        });
        sweepIfDue(now);
    }

    /** 창이 한 번 지날 때마다 한 번, 창 안에 기록이 없는 IP 를 지운다. */
    private void sweepIfDue(Instant now) {
        if (Duration.between(lastSweep, now).compareTo(WINDOW) < 0) return;
        lastSweep = now;
        logins.entrySet()
                .removeIf(entry -> recentOf(entry.getValue(), now).findAny().isEmpty());
    }

    private static Stream<Instant> recentOf(List<Instant> times, Instant now) {
        Instant since = now.minus(WINDOW);
        return times.stream().filter(it -> it.isAfter(since));
    }
}
