package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.coaching.application.ApproveCoachRunView
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunAcceptedView
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunService
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunView
import kr.ac.kookmin.familyfitness.coaching.application.RejectCoachRunView
import kr.ac.kookmin.familyfitness.coaching.application.StartCoachRunCommand
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 코치 실행: 시작(202, 비동기) · 조회 · 승인(보호자) · 거절(보호자). */
@RestController
@RequestMapping("/api/v1")
class CoachRunController(
    private val service: CoachRunService,
) {
    @PostMapping("/families/{familyId}/coach/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(
        user: CurrentUser,
        @PathVariable familyId: UUID,
        @Valid @RequestBody(required = false) body: StartCoachRunRequest?,
    ): CoachRunAcceptedView {
        val request = body ?: StartCoachRunRequest()
        return service.start(user.userId, familyId, StartCoachRunCommand(request.weekStart, request.daysPerWeek, request.minutesPerSession))
    }

    @GetMapping("/coach/runs/{runId}")
    fun get(
        user: CurrentUser,
        @PathVariable runId: UUID,
    ): CoachRunView = service.get(user.userId, runId)

    @PostMapping("/coach/runs/{runId}/approve")
    fun approve(
        user: CurrentUser,
        @PathVariable runId: UUID,
    ): ApproveCoachRunView = service.approve(user.userId, runId)

    @PostMapping("/coach/runs/{runId}/reject")
    fun reject(
        user: CurrentUser,
        @PathVariable runId: UUID,
        @Valid @RequestBody(required = false) body: RejectCoachRunRequest?,
    ): RejectCoachRunView = service.reject(user.userId, runId, body?.reason)
}
