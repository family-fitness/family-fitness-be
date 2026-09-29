package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.CompleteSessionCommand;
import kr.ac.kookmin.familyfitness.coaching.application.ConfirmParticipantView;
import kr.ac.kookmin.familyfitness.coaching.application.CreateMissionCommand;
import kr.ac.kookmin.familyfitness.coaching.application.MissionActivityService;
import kr.ac.kookmin.familyfitness.coaching.application.MissionCreatedView;
import kr.ac.kookmin.familyfitness.coaching.application.MissionDeletionService;
import kr.ac.kookmin.familyfitness.coaching.application.MissionFeedbackCommand;
import kr.ac.kookmin.familyfitness.coaching.application.MissionFeedbackService;
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
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidInputException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus;
import kr.ac.kookmin.familyfitness.shared.persistence.SqlErrors;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
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

/** 미션 직접 만들기(보호자, 한 건 · 여러 날) · 목록 · 단건 · 지우기 · 느낌 · 보호자 확인 · 운동 한 칸 끝 · 미션 경로의 활동 기록(걸음수·타이머). */
@RestController
@RequestMapping("/api/v1")
public class MissionController {
    private final MissionService missions;
    private final MissionDeletionService deletion;
    private final MissionFeedbackService feedbacks;
    private final MissionActivityService activity;
    private final SessionCompletionService sessions;

    public MissionController(
            MissionService missions,
            MissionDeletionService deletion,
            MissionFeedbackService feedbacks,
            MissionActivityService activity,
            SessionCompletionService sessions) {
        this.missions = missions;
        this.deletion = deletion;
        this.feedbacks = feedbacks;
        this.activity = activity;
        this.sessions = sessions;
    }

    /**
     * 직접 만들기. {@code dates} 가 있으면 날마다 하루짜리 한 건씩(같은 날은 한 번, 날짜 차례), 없으면 startDate~endDate 한 건.
     * 날짜 칸을 섞어 보내거나 하나도 안 보내면 400 이다. 규칙 · 차례는 {@link MissionService#createAll} — 전부 되거나 전부 안 된다.
     */
    @PostMapping("/families/{familyId}/missions")
    @ResponseStatus(HttpStatus.CREATED)
    public MissionCreatedView create(
            CurrentUser user, @PathVariable UUID familyId, @Valid @RequestBody CreateMissionRequest body) {
        List<MissionSession> sessions = sessionsOf(body);
        List<CreateMissionCommand> commands = periodsOf(body).stream()
                .map(period -> new CreateMissionCommand(
                        body.title(),
                        period.startDate(),
                        period.endDate(),
                        Objects.requireNonNull(body.targetMetric()),
                        Objects.requireNonNull(body.targetValue()),
                        body.videoId(),
                        body.participantProfileIds(),
                        sessions))
                .toList();
        return missions.createAll(user.userId(), familyId, commands);
    }

    /**
     * 보호자가 미션을 지운다. 아무도 칸을 끝내지 않았고 기간이 끝나지 않은 미션만 — 규칙 · 차례는 {@link MissionDeletionService}.
     * 성공하면 204.
     */
    @DeleteMapping("/missions/{missionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser user, @PathVariable UUID missionId) {
        deletion.delete(user.userId(), missionId);
    }

    /** 운동이 어땠는지 남긴다(참여자 한 명에 한 줄, 다시 보내면 덮어쓴다). 성공하면 204. 규칙은 {@link MissionFeedbackService}. */
    @PostMapping("/missions/{missionId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void feedback(
            CurrentUser user, @PathVariable UUID missionId, @Valid @RequestBody MissionFeedbackRequest body) {
        feedbacks.send(
                user.userId(),
                missionId,
                new MissionFeedbackCommand(
                        Objects.requireNonNull(body.profileId()), Objects.requireNonNull(body.feel())));
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
     * {@link SessionCompletionService}. 같은 미션의 칸 끝은 미션 행 잠금으로 차례로 돌아, 같은 칸 요청 둘이 동시에 오면 늦은 쪽은
     * 먼저 온 쪽의 커밋을 기다렸다가 「이미 끝낸 칸」(200 · xpGained 0)으로 답한다. 그래도 유니크 · 기본 키 위반(SQLSTATE 23505)이
     * 오면(예: 다른 미션의 칸 끝과 같은 날 활동 행을 동시에 처음 넣음) 먼저 온 쪽이 커밋했으니 한 번 더 부른다(409 로 떨어뜨리지 않는다).
     * 다시 부르는 것은 한 번뿐이다. FK · NOT NULL · check 위반은 동시 요청이 아니라
     * 서버 버그라 그대로 던져 {@code ApiErrorHandler} 가 500 으로 남긴다. 두 번째도 유니크 위반이면 그대로 409 가 된다.
     *
     * <p>{@code /done} 은 전환기 별칭이다 — 지금 FE(fe:src/lib/api/queries.ts useCompleteSession)가 부르는 이름이라 같은
     * 핸들러로 받는다. 문서에는 deprecated 로 싣고(OpenApiConfig.TRANSITIONAL_ALIASES), FE 가 {@code /complete} 로 옮기면 걷는다.
     */
    @PostMapping({"/missions/{missionId}/sessions/{seq}/complete", "/missions/{missionId}/sessions/{seq}/done"})
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

    /** 만들 미션 한 건의 기간(양끝 포함). */
    private record Period(LocalDate startDate, LocalDate endDate) {}

    /**
     * 만들 미션마다 기간. dates 면 같은 날을 한 번만, 이른 날부터. startDate · endDate 는 둘 다 있어야 하고 끝날이 시작일보다
     * 앞이면 400 이다.
     */
    private static List<Period> periodsOf(CreateMissionRequest body) {
        List<LocalDate> dates = body.dates();
        LocalDate startDate = body.startDate();
        LocalDate endDate = body.endDate();
        if (dates != null) {
            if (startDate != null || endDate != null) {
                throw new InvalidInputException("dates 와 startDate · endDate 는 같이 보낼 수 없습니다");
            }
            return dates.stream()
                    .distinct()
                    .sorted()
                    .map(day -> new Period(day, day))
                    .toList();
        }
        if (startDate == null || endDate == null) {
            throw new InvalidInputException("startDate · endDate 또는 dates 가 필요합니다");
        }
        if (endDate.isBefore(startDate)) throw new InvalidInputException("endDate 는 startDate 이후여야 합니다");
        return List.of(new Period(startDate, endDate));
    }

    private static List<MissionSession> sessionsOf(CreateMissionRequest body) {
        List<MissionSessionRequest> sessions = body.sessions();
        return sessions == null
                ? List.of()
                : sessions.stream().map(MissionSessionRequest::toDomain).toList();
    }
}
