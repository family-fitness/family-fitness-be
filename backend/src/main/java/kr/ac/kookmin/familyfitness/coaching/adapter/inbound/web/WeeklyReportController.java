package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.WeeklyReportService;
import kr.ac.kookmin.familyfitness.coaching.application.WeeklyReportView;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 주간 요약(월~일). */
@RestController
@RequestMapping("/api/v1/families/{familyId}/report")
public class WeeklyReportController {
    private final WeeklyReportService service;

    public WeeklyReportController(WeeklyReportService service) {
        this.service = service;
    }

    @GetMapping("/weekly")
    public WeeklyReportView weekly(
            CurrentUser user,
            @PathVariable UUID familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable
                    LocalDate weekStart) {
        return service.weekly(user.userId(), familyId, weekStart);
    }
}
