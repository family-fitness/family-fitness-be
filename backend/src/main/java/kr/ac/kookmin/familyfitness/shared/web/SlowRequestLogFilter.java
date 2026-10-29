package kr.ac.kookmin.familyfitness.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 오래 걸린 API 요청을 로그에 남긴다. 운영에서 「화면이 느리다」 는 말이 나오면 어느 주소가 느린지 journald 로그로 찾기 위해서다.
 * <ul>
 *   <li>요청 하나가 {@code app.web.slow-request-threshold}(기본 1초) 이상 걸리면 WARN 한 줄을 남긴다.
 *       예: {@code 느린 요청 GET /api/v1/families/{familyId}/calendar → 200, 1234ms}
 *   <li>주소는 실제 경로가 아니라 컨트롤러의 경로 틀로 적는다. 가족 · 프로필 id 가 로그에 남지 않게 하고, 같은 화면끼리 모아 보기 위해서다.
 *       틀을 모르는 요청(없는 주소 등)은 {@code (경로 틀 없음)} 으로 적는다. 쿼리 문자열도 적지 않는다.
 *   <li>보안 필터보다 앞에 두어 인증 검사까지 포함한 시간을 잰다. 0 이하로 두면 끈다.
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SlowRequestLogFilter extends OncePerRequestFilter {
    static final String UNKNOWN_PATTERN = "(경로 틀 없음)";

    private static final Logger log = LoggerFactory.getLogger(SlowRequestLogFilter.class);

    private final Duration threshold;
    private final LongSupplier nanoTime;

    @Autowired
    public SlowRequestLogFilter(@Value("${app.web.slow-request-threshold:PT1S}") Duration threshold) {
        this(threshold, System::nanoTime);
    }

    SlowRequestLogFilter(Duration threshold, LongSupplier nanoTime) {
        this.threshold = threshold;
        this.nanoTime = nanoTime;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return threshold.isZero()
                || threshold.isNegative()
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = nanoTime.getAsLong();
        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = Duration.ofNanos(nanoTime.getAsLong() - started).toMillis();
            if (tookMs >= threshold.toMillis()) {
                log.warn(
                        "느린 요청 {} {} → {}, {}ms",
                        request.getMethod(),
                        patternOf(request),
                        response.getStatus(),
                        tookMs);
            }
        }
    }

    private static String patternOf(HttpServletRequest request) {
        @Nullable Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String s && !s.isBlank() ? s : UNKNOWN_PATTERN;
    }
}
