package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.ConfirmParticipantView;
import kr.ac.kookmin.familyfitness.coaching.application.CreateMissionCommand;
import kr.ac.kookmin.familyfitness.coaching.application.MissionActivityService;
import kr.ac.kookmin.familyfitness.coaching.application.MissionCreatedView;
import kr.ac.kookmin.familyfitness.coaching.application.MissionListView;
import kr.ac.kookmin.familyfitness.coaching.application.MissionScope;
import kr.ac.kookmin.familyfitness.coaching.application.MissionService;
import kr.ac.kookmin.familyfitness.coaching.application.RecordStepsCommand;
import kr.ac.kookmin.familyfitness.coaching.application.RecordTimerCommand;
import kr.ac.kookmin.familyfitness.coaching.application.StepsRecordedView;
import kr.ac.kookmin.familyfitness.coaching.application.TimerRecordedView;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 미션 직접 만들기(보호자) · 목록 · 보호자 확인 · 미션 경로의 활동 기록(걸음수·타이머). */
@RestController
@RequestMapping("/api/v1")
public class MissionController {
    private final MissionService missions;
    private final MissionActivityService activity;

    public MissionController(MissionService missions, MissionActivityService activity) {
        this.missions = missions;
        this.activity = activity;
    }

    @PostMapping("/families/{familyId}/missions")
    @ResponseStatus(HttpStatus.CREATED)
    public MissionCreatedView create(
            CurrentUser user, @PathVariable UUID familyId, @Valid @RequestBody CreateMissionRequest body) {
        if (Objects.requireNonNull(body.endDate()).isBefore(Objects.requireNonNull(body.startDate()))) {
            throw new IllegalArgumentException("endDate 는 startDate 이후여야 합니다");
        }
        return missions.create(
                user.userId(),
                familyId,
                new CreateMissionCommand(
                        body.title(),
                        body.startDate(),
                        body.endDate(),
                        Objects.requireNonNull(body.targetMetric()),
                        Objects.requireNonNull(body.targetValue()),
                        body.videoId(),
                        body.participantProfileIds()));
    }

    @GetMapping("/families/{familyId}/missions")
    public MissionListView list(
            CurrentUser user,
            @PathVariable UUID familyId,
            @RequestParam(defaultValue = "ALL") MissionScope scope,
            @RequestParam(required = false) @Nullable MissionStatus status) {
        return missions.list(user.userId(), familyId, scope, status);
    }

    @PostMapping("/missions/{missionId}/participants/{profileId}/confirm")
    public ConfirmParticipantView confirm(
            CurrentUser user, @PathVariable UUID missionId, @PathVariable UUID profileId) {
        return missions.confirm(user.userId(), missionId, profileId);
    }

    @PostMapping("/missions/{missionId}/activity/steps")
    public StepsRecordedView steps(
            CurrentUser user, @PathVariable UUID missionId, @Valid @RequestBody RecordStepsRequest body) {
        return activity.recordSteps(
                user.userId(),
                missionId,
                new RecordStepsCommand(
                        Objects.requireNonNull(body.profileId()),
                        Objects.requireNonNull(body.activityDate()),
                        Objects.requireNonNull(body.steps())));
    }

    @PostMapping("/missions/{missionId}/activity/timer")
    public TimerRecordedView timer(
            CurrentUser user, @PathVariable UUID missionId, @Valid @RequestBody RecordTimerRequest body) {
        return activity.recordTimer(
                user.userId(),
                missionId,
                new RecordTimerCommand(
                        Objects.requireNonNull(body.profileId()),
                        Objects.requireNonNull(body.startedAt()),
                        Objects.requireNonNull(body.endedAt()),
                        Objects.requireNonNull(body.activeMinutes())));
    }
}
