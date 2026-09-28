package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyClaimAttemptsException;
import org.springframework.stereotype.Component;

/**
 * 초대코드 수락 · 미리 보기에서 없는 코드(CODE_NOT_FOUND)를 넣은 횟수를 계정마다 센다.
 * 최근 {@link #WINDOW} 안에 {@link #MAX_FAILURES} 번 틀렸으면 다음 요청은 코드를 찾아보기 전에 429 TOO_MANY 다.
 *
 * <p>셈은 sliding window log 다 — 계정마다 틀린 시각을 최근 것만 남겨 두고, 창 안의 개수를 센다. 막힌 동안에는 맞는 코드도
 * 답하지 않는다(맞았는지가 응답으로 새지 않게). 막힌 요청은 세지 않으므로 마지막으로 틀린 뒤 창이 지나면 풀린다.
 *
 * <p>저장은 이 프로세스의 메모리다. 서버가 한 대이고(배포 구성에 여러 대가 없다), 값이 의미 있는 기간이 10분이라 재시작으로 잃어도
 * 한 계정이 더 넣을 수 있는 것은 한 번에 {@link #MAX_FAILURES} 번이다. 틀릴 때마다 DB 에 쓰지 않아도 되고, 코드 찾기가 실패해
 * 트랜잭션이 롤백돼도 셈이 남는다. 여러 대로 띄우면 DB 표나 공유 저장소로 옮겨야 한다(대수만큼 한도가 늘어난다).
 */
@Component
public class ClaimAttemptLimiter {
    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final IdentityClock clock;
    private final ConcurrentHashMap<UUID, List<Instant>> failures = new ConcurrentHashMap<>();
    private volatile Instant lastSweep = Instant.EPOCH;

    public ClaimAttemptLimiter(IdentityClock clock) {
        this.clock = clock;
    }

    /** 코드를 찾기 전에 부른다. 창 안에서 이미 한도만큼 틀렸으면 막는다. */
    public void check(UUID userId) {
        Instant now = clock.now();
        List<Instant> recent = failures.getOrDefault(userId, List.of());
        if (recentOf(recent, now).count() >= MAX_FAILURES) throw new TooManyClaimAttemptsException();
    }

    /** 없는 코드였다. 창 밖의 기록은 이때 버린다. */
    public void recordFailure(UUID userId) {
        Instant now = clock.now();
        failures.compute(userId, (key, previous) -> {
            List<Instant> kept =
                    previous == null ? List.of() : recentOf(previous, now).toList();
            List<Instant> next = Stream.concat(kept.stream(), Stream.of(now)).toList();
            return next.size() > MAX_FAILURES ? next.subList(next.size() - MAX_FAILURES, next.size()) : next;
        });
        sweepIfDue(now);
    }

    /** 창이 한 번 지날 때마다 한 번, 창 안에 기록이 없는 계정을 지운다. 틀리고 다시 오지 않은 계정이 쌓이지 않게. */
    private void sweepIfDue(Instant now) {
        if (Duration.between(lastSweep, now).compareTo(WINDOW) < 0) return;
        lastSweep = now;
        failures.entrySet()
                .removeIf(entry -> recentOf(entry.getValue(), now).findAny().isEmpty());
    }

    private static Stream<Instant> recentOf(List<Instant> times, Instant now) {
        Instant since = now.minus(WINDOW);
        return times.stream().filter(it -> it.isAfter(since));
    }
}
