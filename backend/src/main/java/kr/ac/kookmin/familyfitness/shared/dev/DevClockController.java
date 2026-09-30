package kr.ac.kookmin.familyfitness.shared.dev;

import java.time.Duration;
import java.time.OffsetDateTime;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발용 시간 이동 주소. `app.dev.time-travel.enabled=true`(local · compose)일 때만 빈으로 등록된다. 로그인한 누구나 부를 수 있다 —
 * 로컬 시연 서버 하나를 여럿이 같이 보는 용도라서.
 *
 * <pre>
 * GET  /api/v1/dev/clock                              → {now, today, offset}
 * POST /api/v1/dev/clock {"by":"P1D"}                 → 하루 뒤로. 건너뛴 정시 작업을 원래 시각에 돌린다
 * POST /api/v1/dev/clock {"to":"2026-10-01T07:31:00+09:00"} → 그 시각으로
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/dev/clock")
@ConditionalOnProperty(name = "app.dev.time-travel.enabled", havingValue = "true")
public class DevClockController {
    private final TimeTravel travel;

    public DevClockController(TimeTravel travel) {
        this.travel = travel;
    }

    @GetMapping
    public TimeTravel.ClockView now() {
        return travel.now();
    }

    @PostMapping
    public TimeTravel.Trip travel(@RequestBody TravelRequest request) {
        if ((request.to() == null) == (request.by() == null)) {
            throw new DomainException("TIME_TRAVEL_TARGET", ErrorKind.BAD_REQUEST, "to(시각)와 by(폭) 가운데 하나만 준다");
        }
        if (request.to() != null) return travel.travelTo(request.to().toInstant());
        return travel.travelBy(request.by());
    }

    /** {@code to} 는 오프셋이 붙은 ISO 시각, {@code by} 는 ISO 기간(P1D · PT2H). */
    public record TravelRequest(
            @Nullable OffsetDateTime to, @Nullable Duration by) {}
}
