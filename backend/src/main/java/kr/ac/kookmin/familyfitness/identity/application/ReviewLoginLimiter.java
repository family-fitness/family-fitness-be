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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyReviewLoginsException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정 로그인 횟수를 센다. 부를 때마다 계정 하나 · 가족 하나 · 프로필 넷 · 측정 둘이 생기니, 누가 반복해서 부르면 DB 가 끝없이 찬다.
 * 한도는 둘이다. 최근 {@link #WINDOW} 안에
 * <ul>
 *   <li>같은 IP 에서 {@link #MAX_LOGINS} 번 — 심사위원 한 사람이 넉넉히 쓰는 양. 넘기면 429 TOO_MANY.
 *   <li>IP 와 상관없이 새 계정을 모두 합쳐 {@code maxTotal}(기본 {@link #MAX_TOTAL})개 — IP 를 바꿔 가며 부르거나(X-Forwarded-For 를
 *       꾸며 넣는 것 포함) 프록시 설정이 빠져 IP 가 하나로 보여도, 한 시간에 쌓이는 계정 수는 여기서 멈춘다. 이 한도에 닿으면 429 를 주지
 *       않고 그 한 시간 안에 만든 심사용 계정 가운데 하나를 무작위로 준다({@link Admission#reuse()}) — 누구 한 사람이 한도를 채워 모든
 *       심사위원을 한 시간 동안 막지 못하게. 그 계정은 다른 사람도 받았을 수 있어 기록이 섞일 수 있지만, 못 들어오는 것보다 낫다.
 *       나눠 줄 계정이 없으면(만들다 실패해 적힌 계정이 없음) 429 다.
 * </ul>
 *
 * <p>IPv4 는 주소 하나를, IPv6 는 앞 56비트(/56)를 한 IP 로 센다. 통신사는 보통 집 한 곳에 /56(= /64 대역 256개)을 주고, 기기 하나도
 * /64 를 통째로 받아 뒤 비트를 마음대로 바꿀 수 있다. /64 로 세면 집 한 곳이 IP 256개를 가진 것과 같다.
 *
 * <p>셈은 {@link ClaimAttemptLimiter} 와 같은 sliding window log 다. 통과한 요청만 센다(막힌 요청은 세지 않는다) — 그래서
 * 마지막으로 통과한 뒤 창이 지나면 풀린다. 계정을 나눠 준 로그인은 IP 셈에는 들어가고 새 계정 셈에는 들어가지 않는다. 확인과 기록을
 * 한 잠금 안에서 해서, 동시에 여러 번 불러도 한도를 넘지 않는다.
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

    /** 이번 로그인을 받는 방법. {@code reuse} 가 null 이면 새 계정을 만들고, 아니면 그 심사용 계정으로 들인다. */
    public record Admission(@Nullable UUID reuse) {
        static final Admission NEW = new Admission(null);
    }

    private record Made(Instant at, UUID userId) {}

    private final IdentityClock clock;
    private final int maxTotal;
    private final Map<String, Deque<Instant>> byKey = new HashMap<>();
    private final Deque<Instant> all = new ArrayDeque<>();
    private final Deque<Made> made = new ArrayDeque<>();

    public ReviewLoginLimiter(
            IdentityClock clock, @Value("${app.auth.review-login.max-total:" + MAX_TOTAL + "}") int maxTotal) {
        this.clock = clock;
        this.maxTotal = maxTotal;
    }

    /**
     * IP 한도 안이면 이번 요청을 센다. 새 계정 한도가 남았으면 {@link Admission#NEW}, 찼으면 최근에 만든 심사용 계정 하나를 준다.
     * IP 한도에 닿았거나, 새 계정 한도가 찼는데 나눠 줄 계정이 없으면 세지 않고 {@link TooManyReviewLoginsException}.
     */
    public synchronized Admission acquire(String clientIp) {
        Instant now = clock.now();
        Instant since = now.minus(WINDOW);
        dropOld(all, since);
        while (!made.isEmpty() && !made.peekFirst().at().isAfter(since)) made.removeFirst();
        byKey.values().forEach(it -> dropOld(it, since));
        byKey.values().removeIf(Deque::isEmpty);

        String key = keyOf(clientIp);
        Deque<Instant> mine = byKey.getOrDefault(key, new ArrayDeque<>());
        if (mine.size() >= MAX_LOGINS) throw new TooManyReviewLoginsException();
        Admission admission = Admission.NEW;
        if (all.size() >= maxTotal) {
            if (made.isEmpty()) throw new TooManyReviewLoginsException();
            Made[] recent = made.toArray(Made[]::new);
            admission = new Admission(recent[ThreadLocalRandom.current().nextInt(recent.length)].userId());
        } else {
            all.addLast(now);
        }
        mine.addLast(now);
        byKey.put(key, mine);
        return admission;
    }

    /** 새로 만든 심사용 계정을 적어 둔다 — 새 계정 한도가 찼을 때 나눠 줄 후보다. 계정이 커밋된 뒤에 부른다. */
    public synchronized void remember(UUID userId) {
        made.addLast(new Made(clock.now(), userId));
    }

    /**
     * 셀 때 쓰는 IP 이름. IPv4(IPv4 에 대응된 IPv6 포함)는 주소 그대로, IPv6 는 앞 56비트. IP 글자로 읽히지 않으면 받은 글자 그대로다.
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
            return HexFormat.of().formatHex(bytes, 0, 7) + "::/56";
        }
        return address.getHostAddress();
    }

    private static void dropOld(Deque<Instant> times, Instant since) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(since)) times.removeFirst();
    }
}
