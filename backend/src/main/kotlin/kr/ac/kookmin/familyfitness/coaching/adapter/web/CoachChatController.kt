package kr.ac.kookmin.familyfitness.coaching.adapter.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.coaching.application.ChatCommand
import kr.ac.kookmin.familyfitness.coaching.application.ChatView
import kr.ac.kookmin.familyfitness.coaching.application.CoachChatService
import kr.ac.kookmin.familyfitness.coaching.application.WeeklyReportService
import kr.ac.kookmin.familyfitness.coaching.application.WeeklyReportView
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/** 코치 대화(근거 있는 답만; 거부는 200). */
@RestController
@RequestMapping("/api/v1/coach/chat")
class CoachChatController(
    private val service: CoachChatService,
) {
    @PostMapping
    fun chat(
        user: CurrentUser,
        @Valid @RequestBody body: ChatRequest,
    ): ChatView = service.chat(user.userId, ChatCommand(body.profileId, body.conversationId, body.question))
}

/** 주간 요약(월~일). */
@RestController
@RequestMapping("/api/v1/families/{familyId}/report")
class WeeklyReportController(
    private val service: WeeklyReportService,
) {
    @GetMapping("/weekly")
    fun weekly(
        user: CurrentUser,
        @PathVariable familyId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStart: LocalDate?,
    ): WeeklyReportView = service.weekly(user.userId, familyId, weekStart)
}
