package kr.ac.kookmin.familyfitness.coaching.adapter.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.coaching.application.ConfirmParticipantView
import kr.ac.kookmin.familyfitness.coaching.application.CreateMissionCommand
import kr.ac.kookmin.familyfitness.coaching.application.MissionActivityService
import kr.ac.kookmin.familyfitness.coaching.application.MissionCreatedView
import kr.ac.kookmin.familyfitness.coaching.application.MissionListView
import kr.ac.kookmin.familyfitness.coaching.application.MissionScope
import kr.ac.kookmin.familyfitness.coaching.application.MissionService
import kr.ac.kookmin.familyfitness.coaching.application.RecordStepsCommand
import kr.ac.kookmin.familyfitness.coaching.application.RecordTimerCommand
import kr.ac.kookmin.familyfitness.coaching.application.StepsRecordedView
import kr.ac.kookmin.familyfitness.coaching.application.TimerRecordedView
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 미션 직접 만들기(보호자) · 목록 · 보호자 확인 · 미션 경로의 활동 기록(걸음수·타이머). */
@RestController
@RequestMapping("/api/v1")
class MissionController(
    private val missions: MissionService,
    private val activity: MissionActivityService,
) {
    @PostMapping("/families/{familyId}/missions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        user: CurrentUser,
        @PathVariable familyId: UUID,
        @Valid @RequestBody body: CreateMissionRequest,
    ): MissionCreatedView {
        require(!body.endDate.isBefore(body.startDate)) { "endDate 는 startDate 이후여야 합니다" }
        return missions.create(
            user.userId,
            familyId,
            CreateMissionCommand(
                title = body.title,
                startDate = body.startDate,
                endDate = body.endDate,
                targetMetric = body.targetMetric,
                targetValue = body.targetValue,
                videoId = body.videoId,
                participantProfileIds = body.participantProfileIds,
            ),
        )
    }

    @GetMapping("/families/{familyId}/missions")
    fun list(
        user: CurrentUser,
        @PathVariable familyId: UUID,
        @RequestParam(defaultValue = "ALL") scope: MissionScope,
        @RequestParam(required = false) status: MissionStatus?,
    ): MissionListView = missions.list(user.userId, familyId, scope, status)

    @PostMapping("/missions/{missionId}/participants/{profileId}/confirm")
    fun confirm(
        user: CurrentUser,
        @PathVariable missionId: UUID,
        @PathVariable profileId: UUID,
    ): ConfirmParticipantView = missions.confirm(user.userId, missionId, profileId)

    @PostMapping("/missions/{missionId}/activity/steps")
    fun steps(
        user: CurrentUser,
        @PathVariable missionId: UUID,
        @Valid @RequestBody body: RecordStepsRequest,
    ): StepsRecordedView = activity.recordSteps(user.userId, missionId, RecordStepsCommand(body.profileId, body.activityDate, body.steps))

    @PostMapping("/missions/{missionId}/activity/timer")
    fun timer(
        user: CurrentUser,
        @PathVariable missionId: UUID,
        @Valid @RequestBody body: RecordTimerRequest,
    ): TimerRecordedView =
        activity.recordTimer(user.userId, missionId, RecordTimerCommand(body.profileId, body.startedAt, body.endedAt, body.activeMinutes))
}
