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
 *       꾸며 넣는 것 포함) 한 시간에 쌓이는 계정 수를 여기서 누른다. 이 한도에 닿아도 429 를 주지 않는다 — 누구 한 사람이 한도를 채워
 *       모든 심사위원을 한 시간 동안 막지 못하게. 대신
 *       <ul>
 *         <li>그 IP 가 이 한 시간 안에 만든 계정이 있으면 그 가운데 가장 최근 것으로 들인다({@link Admission#reuse()}).
 *         <li>없으면 새 계정을 하나 만든다. 그 뒤로 그 IP 는 그 계정으로 들어온다. 그래서 한도가 찬 뒤로는 한 시간에 IP 하나마다 계정
 *             하나씩만 는다.
 *       </ul>
 *       다른 IP 가 만든 계정은 나눠 주지 않는다. 한도를 채운 사람은 자기가 만든 계정의 토큰을 다 쥐고 있어, 그 계정을 남에게 주면 그
 *       사람이 가족 이름 · 식구 · 기록을 바꿔 뒤에 들어온 심사위원에게 보이고, 심사위원이 하는 일도 들여다본다. 같은 IP(같은 와이파이 ·
 *       같은 회사망)끼리는 계정이 겹칠 수 있다.
 * </ul>
 *
 * <p>IPv4 는 주소 하나를, IPv6 는 앞 56비트(/56)를 한 IP 로 센다. 통신사는 보통 집 한 곳에 /56(= /64 대역 256개)을 주고, 기기 하나도
 * /64 를 통째로 받아 뒤 비트를 마음대로 바꿀 수 있다. /64 로 세면 집 한 곳이 IP 256개를 가진 것과 같다.
 *
 * <p>셈은 {@link ClaimAttemptLimiter} 와 같은 sliding window log 다. 통과한 요청만 센다(막힌 요청은 세지 않는다) — 그래서
 * 마지막으로 통과한 뒤 창이 지나면 풀린다. 계정을 다시 준 로그인은 IP 셈에는 들어가고 새 계정 셈에는 들어가지 않는다. 확인과 기록을
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

    /** 이번 로그인을 받는 방법. {@code reuse} 가 null 이면 새 계정을 만들고, 아니면 같은 IP 가 만든 그 심사용 계정으로 들인다. */
    public record Admission(@Nullable UUID reuse) {
        static final Admission NEW = new Admission(null);
    }

    private record Made(Instant at, String key, UUID userId) {}

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
     * IP 한도 안이면 이번 요청을 센다. 새 계정 한도가 남았으면 {@link Admission#NEW}. 찼으면 이 IP 가 이 한 시간에 만든 가장 최근 계정을
     * 주고, 그런 계정이 없으면 {@link Admission#NEW}. IP 한도에 닿았으면 세지 않고 {@link TooManyReviewLoginsException}.
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
            UUID own = latestMadeBy(key);
            if (own != null) admission = new Admission(own);
        }
        if (admission.reuse() == null) all.addLast(now);
        mine.addLast(now);
        byKey.put(key, mine);
        return admission;
    }

    /** 새로 만든 심사용 계정을 만든 IP 와 함께 적어 둔다 — 새 계정 한도가 찼을 때 그 IP 에 다시 줄 후보다. 계정이 커밋된 뒤에 부른다. */
    public synchronized void remember(String clientIp, UUID userId) {
        made.addLast(new Made(clock.now(), keyOf(clientIp), userId));
    }

    private @Nullable UUID latestMadeBy(String key) {
        var it = made.descendingIterator();
        while (it.hasNext()) {
            Made one = it.next();
            if (one.key().equals(key)) return one.userId();
        }
        return null;
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
