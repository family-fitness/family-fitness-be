package kr.ac.kookmin.familyfitness.identity.application;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyReviewLoginsException;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정 로그인 횟수를 센다. 부를 때마다 계정 하나 · 가족 하나 · 프로필 넷 · 측정 둘이 생기니, 누가 반복해서 부르면 DB 가 끝없이 찬다.
 * 한도는 둘이다. 최근 {@link #WINDOW} 안에
 * <ul>
 *   <li>같은 IP 에서 {@link #MAX_LOGINS} 번 — 심사위원 한 사람이 넉넉히 쓰는 양
 *   <li>IP 와 상관없이 모두 합쳐 {@link #MAX_TOTAL} 번 — IP 를 바꿔 가며 부르거나(X-Forwarded-For 를 꾸며 넣는 것 포함)
 *       프록시 설정이 빠져 IP 가 하나로 보여도, 한 시간에 쌓이는 계정 수는 여기서 멈춘다
 * </ul>
 * 둘 중 하나라도 닿았으면 다음 요청은 계정을 만들기 전에 429 TOO_MANY 다.
 *
 * <p>IPv4 는 주소 하나를, IPv6 는 앞 64비트(/64)를 한 IP 로 센다. IPv6 는 집 하나 · 기기 하나가 /64 대역을 통째로 받아
 * 뒤 64비트를 마음대로 바꿀 수 있어서, 주소 하나씩 세면 한도가 없는 것과 같다.
 *
 * <p>셈은 {@link ClaimAttemptLimiter} 와 같은 sliding window log 다. 통과한 요청만 센다(막힌 요청은 세지 않는다) — 그래서
 * 마지막으로 통과한 뒤 창이 지나면 풀린다. 확인과 기록을 한 잠금 안에서 해서, 동시에 여러 번 불러도 한도를 넘지 않는다.
 *
 * <p>저장은 이 프로세스의 메모리다. 서버가 한 대라 이것으로 된다. 재시작하면 셈이 빈다.
 * IP 는 서블릿의 remoteAddr 이다. FE 는 /api/v1/** 를 늘 Next 서버를 거쳐 넘기므로, 운영에서는
 * {@code server.forward-headers-strategy=native}(application-prod.properties 기본값)로 Tomcat 이 믿을 프록시가 붙인
 * X-Forwarded-For 에서 브라우저 IP 를 꺼내 remoteAddr 로 쓴다. 이 설정이 빠지면 모든 심사위원이 Next 서버 IP 하나로 세진다(README).
 */
@Component
public class ReviewLoginLimiter {
    static final int MAX_LOGINS = 30;
    static final int MAX_TOTAL = 300;
    static final Duration WINDOW = Duration.ofHours(1);

    private final IdentityClock clock;
    private final Map<String, Deque<Instant>> byKey = new HashMap<>();
    private final Deque<Instant> all = new ArrayDeque<>();

    public ReviewLoginLimiter(IdentityClock clock) {
        this.clock = clock;
    }

    /** 한도 안이면 이번 요청을 세고 돌아온다. 한도에 닿았으면 세지 않고 {@link TooManyReviewLoginsException}. */
    public synchronized void acquire(String clientIp) {
        Instant now = clock.now();
        Instant since = now.minus(WINDOW);
        dropOld(all, since);
        byKey.values().forEach(it -> dropOld(it, since));
        byKey.values().removeIf(Deque::isEmpty);

        String key = keyOf(clientIp);
        Deque<Instant> mine = byKey.getOrDefault(key, new ArrayDeque<>());
        if (mine.size() >= MAX_LOGINS || all.size() >= MAX_TOTAL) throw new TooManyReviewLoginsException();
        mine.addLast(now);
        byKey.put(key, mine);
        all.addLast(now);
    }

    /**
     * 셀 때 쓰는 IP 이름. IPv4(IPv4 에 대응된 IPv6 포함)는 주소 그대로, IPv6 는 앞 64비트. IP 글자로 읽히지 않으면 받은 글자 그대로다.
     */
    static String keyOf(String clientIp) {
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(clientIp);
        } catch (IllegalArgumentException notAnIp) {
            return clientIp;
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            return HexFormat.of().formatHex(bytes, 0, 8) + "::/64";
        }
        return address.getHostAddress();
    }

    private static void dropOld(Deque<Instant> times, Instant since) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(since)) times.removeFirst();
    }
}
