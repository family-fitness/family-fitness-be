package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.ApproveCoachRunView;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunAcceptedView;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunService;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunView;
import kr.ac.kookmin.familyfitness.coaching.application.RejectCoachRunView;
import kr.ac.kookmin.familyfitness.coaching.application.StartCoachRunCommand;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 코치 실행: 시작(202, 비동기) · 조회 · 승인(보호자) · 거절(보호자). */
@RestController
@RequestMapping("/api/v1")
public class CoachRunController {
    private final CoachRunService service;

    public CoachRunController(CoachRunService service) {
        this.service = service;
    }

    @PostMapping("/families/{familyId}/coach/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CoachRunAcceptedView start(
            CurrentUser user,
            @PathVariable UUID familyId,
            @Valid @RequestBody(required = false) @Nullable StartCoachRunRequest body) {
        StartCoachRunRequest request = body == null ? new StartCoachRunRequest() : body;
        return service.start(
                user.userId(),
                familyId,
                new StartCoachRunCommand(request.weekStart(), request.daysPerWeek(), request.minutesPerSession()));
    }

    @GetMapping("/coach/runs/{runId}")
    public CoachRunView get(CurrentUser user, @PathVariable UUID runId) {
        return service.get(user.userId(), runId);
    }

    @PostMapping("/coach/runs/{runId}/approve")
    public ApproveCoachRunView approve(CurrentUser user, @PathVariable UUID runId) {
        return service.approve(user.userId(), runId);
    }

    @PostMapping("/coach/runs/{runId}/reject")
    public RejectCoachRunView reject(
            CurrentUser user,
            @PathVariable UUID runId,
            @Valid @RequestBody(required = false) @Nullable RejectCoachRunRequest body) {
        return service.reject(user.userId(), runId, body == null ? null : body.reason());
    }
}
