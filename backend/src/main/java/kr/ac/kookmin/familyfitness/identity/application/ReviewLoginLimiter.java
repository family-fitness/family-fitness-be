package kr.ac.kookmin.familyfitness.identity.application;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyReviewLoginsException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정 로그인 횟수를 센다. 부를 때마다 계정이 생기고, FAMILY · INVITED 는 가족 하나 · 프로필 넷 · 측정 둘도 생긴다(INVITED 는
 * 가짜 보호자 계정까지 계정 둘). 누가 반복해서 부르면 DB 가 끝없이 찬다. kind({@link ReviewLoginKind}) 세 가지를 한도 하나로 합쳐 센다.
 * 한도는 둘이다. 최근 {@link #WINDOW} 안에
 * <ul>
 *   <li>같은 IP 에서 {@link #MAX_LOGINS} 번. 심사위원 여럿이 한 와이파이(같은 공인 IP)에서 저마다 세 kind 와 「아이 입장으로
 *       둘러보기」 를 몇 번씩 눌러도 남는 양이다(30번이면 열 명이 저마다 서너 번 누를 때 찬다). 넘기면 429 TOO_MANY.
 *   <li>IP 와 상관없이 새 계정을 모두 합쳐 {@code maxTotal}(기본 {@link #MAX_TOTAL})개 — IP 를 바꿔 가며 부르거나(X-Forwarded-For 를
 *       꾸며 넣는 것 포함) 한 시간에 쌓이는 계정 수를 여기서 누른다. 이 한도에 닿아도 429 를 주지 않는다 — 누구 한 사람이 한도를 채워
 *       모든 심사위원을 한 시간 동안 막지 못하게. 대신
 *       <ul>
 *         <li>그 IP 가 이 한 시간 안에 같은 kind 로 만든 계정이 있으면 그 가운데 가장 최근 것으로 들인다({@link Admission#reuse()}).
 *             kind 가 다른 계정은 주지 않는다 — 가족 만들기부터 해 보려는 심사위원에게 체험 가족이 든 계정을 주면 그 흐름을 볼 수 없다.
 *         <li>없으면 새 계정을 하나 만든다. 그 뒤로 그 IP 는 그 kind 로 부르면 그 계정으로 들어온다. 그래서 한도가 찬 뒤로는 한 시간에
 *             IP 하나마다 kind 하나에 계정 하나씩만 는다.
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
    static final int MAX_LOGINS = 60;
    static final int MAX_TOTAL = 300;
    static final Duration WINDOW = Duration.ofHours(1);

    /**
     * 이번 로그인을 받는 방법. {@code reuse} 가 null 이면 새 계정을 만들고, 아니면 같은 IP 가 같은 kind 로 만든 그 심사용 계정으로 들인다.
     *
     * @param inviteCode 다시 주는 계정을 INVITED 로 만들었으면 그때 준 초대코드. 아니면 null
     */
    public record Admission(@Nullable UUID reuse, @Nullable String inviteCode) {
        static final Admission NEW = new Admission(null, null);
    }

    private record Made(
            Instant at,
            String key,
            ReviewLoginKind kind,
            UUID userId,
            @Nullable String inviteCode) {}

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
     * IP 한도 안이면 이번 요청을 센다(kind 와 상관없이 합쳐 센다). 새 계정 한도가 남았으면 {@link Admission#NEW}. 찼으면 이 IP 가 이 한
     * 시간에 같은 kind 로 만든 가장 최근 계정을 주고, 그런 계정이 없으면 {@link Admission#NEW}. IP 한도에 닿았으면 세지 않고
     * {@link TooManyReviewLoginsException}.
     */
    public synchronized Admission acquire(String clientIp, ReviewLoginKind kind) {
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
            Made own = latestMadeBy(key, kind);
            if (own != null) admission = new Admission(own.userId(), own.inviteCode());
        }
        if (admission.reuse() == null) all.addLast(now);
        mine.addLast(now);
        byKey.put(key, mine);
        return admission;
    }

    /**
     * 새로 만든 심사용 계정을 만든 IP · kind 와 함께 적어 둔다 — 새 계정 한도가 찼을 때 그 IP 가 같은 kind 로 부르면 다시 줄 후보다. 계정이
     * 커밋된 뒤에 부른다.
     *
     * @param inviteCode INVITED 로 만든 계정이면 그때 준 초대코드. 다시 줄 때 같은 코드를 싣는다
     */
    public synchronized void remember(String clientIp, ReviewLoginKind kind, UUID userId, @Nullable String inviteCode) {
        made.addLast(new Made(clock.now(), keyOf(clientIp), kind, userId, inviteCode));
    }

    /** 탈퇴한 계정을 다시 줄 후보에서 뺀다. 지운 계정으로 들이면 토큰 기록을 넣다가 외래 키에 걸린다. */
    public synchronized void forget(UUID userId) {
        made.removeIf(it -> it.userId().equals(userId));
    }

    private @Nullable Made latestMadeBy(String key, ReviewLoginKind kind) {
        var it = made.descendingIterator();
        while (it.hasNext()) {
            Made one = it.next();
            if (one.key().equals(key) && one.kind() == kind) return one;
        }
        return null;
    }

    /**
     * 셀 때 쓰는 IP 이름. IPv4(IPv4 에 대응된 IPv6 포함)는 주소 그대로, IPv6 는 앞 56비트 대역을 표준 표기(RFC 5952, 예
     * {@code 2001:db8:abcd:1200::/56})로 쓴다 — 로그에도 이 글자가 나가서, 배포 점검 때 휴대폰 공인 IP 와 눈으로 맞춰 본다.
     * IP 글자로 읽히지 않으면 받은 글자 그대로다.
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
            Arrays.fill(bytes, 7, bytes.length, (byte) 0);
            return standard(bytes) + "/56";
        }
        return address.getHostAddress();
    }

    /**
     * 로그에 남기는 IP. 끝자리를 가린다 — IPv4(IPv4 에 대응된 IPv6 포함)는 마지막 옥텟을 {@code *} 로(예 {@code 203.0.113.*}), IPv6 는
     * 셀 때와 같은 /56 대역(뒤 72비트를 지운 값)이다. IP 글자로 읽히지 않으면 받은 글자를 남기지 않는다. 배포 점검(README)은 부른
     * 기기의 공인 IP 와 앞 세 옥텟 · /56 대역을 견주면 된다. 셀 때 쓰는 이름({@link #keyOf})은 메모리에만 있고 로그에 나가지 않는다.
     */
    public static String maskedForLog(String clientIp) {
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(clientIp);
        } catch (IllegalArgumentException notAnIp) {
            return "(IP 아님)";
        }
        if (address instanceof Inet6Address) return keyOf(clientIp);
        String ipv4 = address.getHostAddress();
        return ipv4.substring(0, ipv4.lastIndexOf('.') + 1) + "*";
    }

    /**
     * IPv6 16바이트를 RFC 5952 표기로. 16비트 묶음 여덟 개를 앞 0 없이 소문자 16진수로 쓰고, 0 인 묶음이 둘 이상 이어진 곳 가운데 가장 긴
     * 곳(길이가 같으면 앞의 곳) 하나를 {@code ::} 로 줄인다. {@link InetAddress#getHostAddress()} 는 줄이지 않는다
     * ({@code 2001:db8:0:0:0:0:0:0}).
     */
    static String standard(byte[] bytes) {
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) groups[i] = ((bytes[2 * i] & 0xff) << 8) | (bytes[2 * i + 1] & 0xff);
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && groups[i] == 0) i++;
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                out.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!out.isEmpty() && out.charAt(out.length() - 1) != ':') out.append(':');
            out.append(Integer.toHexString(groups[i]));
        }
        return out.toString();
    }

    private static void dropOld(Deque<Instant> times, Instant since) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(since)) times.removeFirst();
    }
}
