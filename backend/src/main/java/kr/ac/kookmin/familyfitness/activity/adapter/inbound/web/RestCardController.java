package kr.ac.kookmin.familyfitness.activity.adapter.inbound.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.application.RestCardService;
import kr.ac.kookmin.familyfitness.activity.application.RestCardsView;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쉬는 날 카드. 보기는 같은 가족 누구나, 쓰기 · 되돌리기는 보호자만. 세 주소 모두 그달 카드 모양으로 답한다.
 *
 * <p>{@code /rest-days} 는 전환기 별칭이다 — 지금 FE(fe:src/lib/api/queries.ts useRestDays · useRestDay)가 부르는 이름이라
 * 같은 핸들러로 받는다. 문서에는 deprecated 로 싣고(OpenApiConfig.TRANSITIONAL_ALIASES), FE 가 {@code /rest-cards} 로 옮기면
 * 걷는다.
 */
@RestController
@RequestMapping({"/api/v1/families/{familyId}/rest-cards", "/api/v1/families/{familyId}/rest-days"})
public class RestCardController {
    private final RestCardService service;

    public RestCardController(RestCardService service) {
        this.service = service;
    }

    /** {@code month}(YYYY-MM)가 없으면 이번 달(KST). */
    @GetMapping
    public RestCardsView month(
            CurrentUser user, @PathVariable UUID familyId, @RequestParam(required = false) @Nullable YearMonth month) {
        return service.month(user.userId(), familyId, month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestCardsView use(
            CurrentUser user,
            @PathVariable UUID familyId,
            @RequestBody(required = false) @Nullable UseRestCardRequest request) {
        return service.use(user.userId(), familyId, request == null ? null : request.date());
    }

    @DeleteMapping("/{restDate}")
    public RestCardsView cancel(
            CurrentUser user,
            @PathVariable UUID familyId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate restDate) {
        return service.cancel(user.userId(), familyId, restDate);
    }
}
