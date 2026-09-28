package kr.ac.kookmin.familyfitness.league.adapter.inbound.web;

import java.time.YearMonth;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.application.LeagueService;
import kr.ac.kookmin.familyfitness.league.application.LeagueView;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 가족 리그 — 같은 가족이면 누구나 본다. 순위표에는 가족 이름과 달성률만 있다. */
@RestController
public class LeagueController {
    private final LeagueService service;

    public LeagueController(LeagueService service) {
        this.service = service;
    }

    /** {@code month}(YYYY-MM)가 없으면 이번 달(KST). 지난달은 정산 기록으로 답한다. */
    @GetMapping("/api/v1/families/{familyId}/league")
    public LeagueView league(
            CurrentUser user, @PathVariable UUID familyId, @RequestParam(required = false) @Nullable YearMonth month) {
        return service.league(user.userId(), familyId, month);
    }
}
