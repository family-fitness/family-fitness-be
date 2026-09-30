package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.CalendarService;
import kr.ac.kookmin.familyfitness.coaching.application.CalendarView;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 날짜별 기록 — 캘린더 · 하루 기록 · 이번 주 링 · 대시보드가 이것 하나로 그린다. 셈과 권한은 {@link CalendarService}. */
@RestController
public class CalendarController {
    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    /**
     * {@code from} ~ {@code to}(양끝 포함, KST 날짜)는 42일까지이고 1900-01-01 ~ 2100-12-31 안이어야 한다. 셋 다 필요하다 — 빠지거나
     * 형식이 틀리거나 범위를 벗어나면 400.
     */
    @GetMapping("/api/v1/families/{familyId}/calendar")
    public CalendarView calendar(
            CurrentUser user,
            @PathVariable UUID familyId,
            @RequestParam UUID profileId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(user.userId(), familyId, profileId, from, to);
    }
}
