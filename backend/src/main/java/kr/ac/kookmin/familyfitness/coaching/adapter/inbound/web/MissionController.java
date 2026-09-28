package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.CompleteSessionCommand;
import kr.ac.kookmin.familyfitness.coaching.application.ConfirmParticipantView;
import kr.ac.kookmin.familyfitness.coaching.application.CreateMissionCommand;
import kr.ac.kookmin.familyfitness.coaching.application.MissionActivityService;
import kr.ac.kookmin.familyfitness.coaching.application.MissionCreatedView;
import kr.ac.kookmin.familyfitness.coaching.application.MissionListView;
import kr.ac.kookmin.familyfitness.coaching.application.MissionScope;
import kr.ac.kookmin.familyfitness.coaching.application.MissionService;
import kr.ac.kookmin.familyfitness.coaching.application.MissionView;
import kr.ac.kookmin.familyfitness.coaching.application.RecordStepsCommand;
import kr.ac.kookmin.familyfitness.coaching.application.RecordTimerCommand;
import kr.ac.kookmin.familyfitness.coaching.application.SessionCompletedView;
import kr.ac.kookmin.familyfitness.coaching.application.SessionCompletionService;
import kr.ac.kookmin.familyfitness.coaching.application.StepsRecordedView;
import kr.ac.kookmin.familyfitness.coaching.application.TimerRecordedView;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus;
import kr.ac.kookmin.familyfitness.shared.persistence.SqlErrors;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 미션 직접 만들기(보호자) · 목록 · 단건 · 보호자 확인 · 운동 한 칸 끝 · 미션 경로의 활동 기록(걸음수·타이머). */
@RestController
@RequestMapping("/api/v1")
public class MissionController {
    private final MissionService missions;
    private final MissionActivityService activity;
    private final SessionCompletionService sessions;

    public MissionController(
            MissionService missions, MissionActivityService activity, SessionCompletionService sessions) {
        this.missions = missions;
        this.activity = activity;
        this.sessions = sessions;
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
                        body.participantProfileIds(),
                        sessionsOf(body)));
    }

    @GetMapping("/families/{familyId}/missions")
    public MissionListView list(
            CurrentUser user,
            @PathVariable UUID familyId,
            @RequestParam(defaultValue = "ALL") MissionScope scope,
            @RequestParam(required = false) @Nullable MissionStatus status) {
        return missions.list(user.userId(), familyId, scope, status);
    }

    @GetMapping("/missions/{missionId}")
    public MissionView get(CurrentUser user, @PathVariable UUID missionId) {
        return missions.get(user.userId(), missionId);
    }

    @PostMapping("/missions/{missionId}/participants/{profileId}/confirm")
    public ConfirmParticipantView confirm(
            CurrentUser user, @PathVariable UUID missionId, @PathVariable UUID profileId) {
        return missions.confirm(user.userId(), missionId, profileId);
    }

    /**
     * 운동 한 칸 끝(FE 요청서 0장 합의 — 설계안 이름 {@code /complete}, seq = position). 판정 · 기록 규칙은
     * {@link SessionCompletionService}. 같은 칸 요청 둘이 동시에 오면 늦은 쪽이 칸 끝 표의 기본 키에 걸려 되돌려진다 —
     * 먼저 온 쪽이 이미 커밋했으니 한 번 더 부르면 「이미 끝낸 칸」(200 · xpGained 0)으로 답한다(409 로 떨어뜨리지 않는다).
     * 다시 부르는 것은 유니크 · 기본 키 위반(SQLSTATE 23505)일 때 한 번뿐이다. FK · NOT NULL · check 위반은 동시 요청이 아니라
     * 서버 버그라 그대로 던져 {@code ApiErrorHandler} 가 500 으로 남긴다. 두 번째도 유니크 위반이면 그대로 409 가 된다.
     */
    @PostMapping("/missions/{missionId}/sessions/{seq}/complete")
    public SessionCompletedView completeSession(
            CurrentUser user,
            @PathVariable UUID missionId,
            @PathVariable int seq,
            @Valid @RequestBody CompleteSessionRequest body) {
        CompleteSessionCommand command = new CompleteSessionCommand(
                Objects.requireNonNull(body.profileId()),
                Objects.requireNonNull(body.activeSeconds()),
                Objects.requireNonNull(body.startedAt()),
                Objects.requireNonNull(body.endedAt()));
        try {
            return sessions.complete(user.userId(), missionId, seq, command);
        } catch (DataIntegrityViolationException raced) {
            if (!SqlErrors.isUniqueViolation(raced)) throw raced;
            return sessions.complete(user.userId(), missionId, seq, command);
        }
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

    private static List<MissionSession> sessionsOf(CreateMissionRequest body) {
        List<MissionSessionRequest> sessions = body.sessions();
        return sessions == null
                ? List.of()
                : sessions.stream().map(MissionSessionRequest::toDomain).toList();
    }
}
