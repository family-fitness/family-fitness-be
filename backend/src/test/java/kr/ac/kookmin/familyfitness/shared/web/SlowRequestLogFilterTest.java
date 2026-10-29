package kr.ac.kookmin.familyfitness.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class SlowRequestLogFilterTest {
    private final AtomicLong now = new AtomicLong();

    /** 요청 처리에 {@code tookMs} 가 걸리는 필터 체인으로 한 번 돌린다. */
    private MockHttpServletResponse run(
            SlowRequestLogFilter filter, MockHttpServletRequest request, long tookMs, int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            now.addAndGet(Duration.ofMillis(tookMs).toNanos());
            ((MockHttpServletResponse) res).setStatus(status);
        });
        return response;
    }

    private static MockHttpServletRequest calendar() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/families/6f1c2e0a-0000-0000-0000-000000000001/calendar");
        request.setQueryString("profileId=6f1c2e0a-0000-0000-0000-000000000002");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/families/{familyId}/calendar");
        return request;
    }

    @Test
    @DisplayName("기준(1초) 이상 걸린 요청은 경로 틀 · 상태 · 걸린 시간을 남기고, 실제 id 와 쿼리 문자열은 남기지 않는다")
    void 느린_요청은_경로_틀로_남긴다(CapturedOutput output) throws Exception {
        SlowRequestLogFilter filter = new SlowRequestLogFilter(Duration.ofSeconds(1), now::get);

        run(filter, calendar(), 1234, 200);

        assertThat(output).contains("느린 요청 GET /api/v1/families/{familyId}/calendar → 200, 1234ms");
        assertThat(output).doesNotContain("6f1c2e0a");
    }

    @Test
    @DisplayName("기준보다 빨리 끝난 요청은 남기지 않는다. 경계(정확히 1초)는 남긴다")
    void 빠른_요청은_남기지_않는다(CapturedOutput output) throws Exception {
        SlowRequestLogFilter filter = new SlowRequestLogFilter(Duration.ofSeconds(1), now::get);

        run(filter, calendar(), 999, 200);
        assertThat(output).doesNotContain("느린 요청");

        run(filter, calendar(), 1000, 200);
        assertThat(output).contains("느린 요청 GET /api/v1/families/{familyId}/calendar → 200, 1000ms");
    }

    @Test
    @DisplayName("경로 틀을 모르는 요청(없는 주소)은 「(경로 틀 없음)」 으로 남긴다")
    void 경로_틀이_없으면_대신_적는다(CapturedOutput output) throws Exception {
        SlowRequestLogFilter filter = new SlowRequestLogFilter(Duration.ofMillis(100), now::get);

        run(filter, new MockHttpServletRequest("POST", "/api/v1/nowhere/123"), 150, 404);

        assertThat(output).contains("느린 요청 POST (경로 틀 없음) → 404, 150ms");
        assertThat(output).doesNotContain("nowhere/123");
    }

    @Test
    @DisplayName("기준이 0 이하면 끄고, /api/ 밖의 요청(스웨거 · 헬스 체크)은 재지 않는다")
    void 끄거나_API_밖이면_재지_않는다(CapturedOutput output) throws Exception {
        SlowRequestLogFilter off = new SlowRequestLogFilter(Duration.ZERO, now::get);
        SlowRequestLogFilter on = new SlowRequestLogFilter(Duration.ofMillis(1), now::get);

        run(off, calendar(), 5000, 200);
        run(on, new MockHttpServletRequest("GET", "/actuator/health"), 5000, 200);

        assertThat(output).doesNotContain("느린 요청");
    }
}
