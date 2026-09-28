package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCompleted;
import kr.ac.kookmin.familyfitness.coaching.api.SessionCompleted;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotActiveException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.NotParticipantException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionTooShortException;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemorySessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.progress.api.ProgressRecorder;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 운동 한 칸 끝 — 판정 차례 · 기록 · 번짐 · 진행도 · 경험치 · 이벤트.
 * 기준 시각은 2026-09-09(수) 10:00 KST({@link Fixed}). 경험치는 가짜 원장(칸 +5 · 미션 +20, 같은 키는 한 번)으로 센다.
 */
class SessionCompletionServiceTest {
    private final Family family = new Family();
    private final FakeIdentity identity = new FakeIdentity(family);
    private final FakeActivity activity = new FakeActivity();
    private final InMemorySessionCompletionRepository completions = new InMemorySessionCompletionRepository();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository(completions);
    private final MissionCompletionPolicy policy =
            new MissionCompletionPolicy(activity, new InMemoryVideoInteractionRepository(), missions, completions);
    private final FakeProgress progress = new FakeProgress(activity);
    private final List<Object> events = new ArrayList<>();
    private final ApplicationEventPublisher publisher = events::add;
    private final SessionCompletionService service = service(Fixed.NOW);
    private final MissionService missionService = new MissionService(
            missions,
            completions,
            new InMemoryExerciseVideoRepository(),
            identity,
            identity,
            policy,
            event -> {},
            Fixed.time());
    private final MissionActivityService activityService =
            new MissionActivityService(missions, identity, activity, activity, policy, Fixed.time());

    private final UUID child = family.child.profileId();
    private final UUID parent = family.parent.profileId();
    private final UUID cheerParent = family.cheerParent.profileId();

    private SessionCompletionService service(Instant now) {
        return new SessionCompletionService(
                missions, completions, identity, identity, activity, progress, policy, publisher, Fixed.time(now));
    }

    /** 칸 끝 경험치의 가짜 원장. 부를 때 그 사람의 활동 분도 적어 둔다 — 활동을 먼저 쌓았는지 본다. */
    static final class FakeProgress implements ProgressRecorder {
        final List<SessionDone> calls = new ArrayList<>();
        final List<Integer> minutesAtCall = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();
        private final FakeActivity activity;

        FakeProgress(FakeActivity activity) {
            this.activity = activity;
        }

        @Override
        public int sessionDone(SessionDone done) {
            calls.add(done);
            minutesAtCall.add(activity.verifiedSummary(done.profileId()).totalMinutes());
            int gained = 0;
            if (keys.add(done.profileId() + ":S:" + done.missionId() + ":" + done.position())) gained += 5;
            if (done.countsAsMissionDone() && keys.add(done.profileId() + ":M:" + done.missionId())) gained += 20;
            return gained;
        }
    }

    private static MissionSession session(int position, SessionPhase phase, int minutes) {
        return new MissionSession(position, phase, "동작" + position, FitnessFactor.FLEXIBILITY, minutes, null);
    }

    /** 오늘 하루짜리 직접 짜기 미션: 준비 1분 · 본 2분 · 정리 1분. */
    private Mission manual(List<UUID> participants) {
        return manual(participants, Fixed.TODAY, Fixed.TODAY);
    }

    private Mission manual(List<UUID> participants, LocalDate from, LocalDate to) {
        return missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "거북이 스트레칭",
                TargetMetric.TIMER_MINUTES,
                4,
                null,
                from,
                to,
                participants,
                List.of(
                        session(1, SessionPhase.WARMUP, 1),
                        session(2, SessionPhase.MAIN, 2),
                        session(3, SessionPhase.COOLDOWN, 1)),
                parent,
                Fixed.NOW));
    }

    /** 코치 미션: 아이(주행자) · 엄마(동반자) · 아빠(응원), 칸은 본운동 2분 하나. */
    private Mission coach() {
        return missions.save(Mission.reconstitute(
                UUID.randomUUID(),
                family.familyId,
                UUID.randomUUID(),
                "유연성 키우기",
                null,
                MissionOrigin.COACH,
                TargetMetric.TIMER_MINUTES,
                2,
                null,
                null,
                Fixed.TODAY,
                Fixed.TODAY,
                parent,
                Fixed.NOW,
                List.of(
                        MissionParticipant.pending(child, CoachRoles.DRIVER, Fixed.NOW),
                        MissionParticipant.pending(parent, CoachRoles.COMPANION, Fixed.NOW),
                        MissionParticipant.pending(cheerParent, CoachRoles.CHEER, Fixed.NOW)),
                List.of(session(1, SessionPhase.MAIN, 2))));
    }

    private Mission sessionless(TargetMetric metric, int target) {
        return missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "옛 운동",
                metric,
                target,
                null,
                Fixed.TODAY,
                Fixed.TODAY,
                List.of(child),
                List.of(),
                parent,
                Fixed.NOW));
    }

    /** activeSeconds 를 재생했고 기기 시각 간격은 넉넉하다. */
    private static CompleteSessionCommand played(UUID profileId, int activeSeconds) {
        Instant endedAt = Fixed.NOW.minusSeconds(5);
        return new CompleteSessionCommand(profileId, activeSeconds, endedAt.minusSeconds(3600), endedAt);
    }

    private SessionCompletedView complete(UUID userId, Mission mission, int position, UUID profileId, int seconds) {
        return service.complete(userId, mission.getId(), position, played(profileId, seconds));
    }

    private int videoSecondsOf(UUID profileId) {
        return activity.seconds.getOrDefault(new FakeActivity.Key(profileId, Fixed.TODAY, ActivitySource.VIDEO), 0);
    }

    private MissionParticipantView viewOf(Mission mission, UUID profileId) {
        return missionService.get(family.parentUser, mission.getId()).participants().stream()
                .filter(it -> it.profileId().equals(profileId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("칸을 끝내면 그 사람의 칸 끝 한 행 · 활동 초(VIDEO) · 끝낸 칸 분 비율 진행도 · +5 가 쌓이고, 마지막 칸이면 완료 · +25")
    void 칸을_끝내면_기록_활동_진행도_경험치가_쌓인다() {
        Mission mission = manual(List.of(child));

        SessionCompletedView first = complete(family.childUser, mission, 2, child, 100);

        assertThat(first.position()).isEqualTo(2);
        assertThat(first.verifiedBy()).isEqualTo(VerifiedBy.VIDEO_PROGRESS);
        assertThat(first.missionProgress()).isEqualTo(0.5); // 2분 / 4분
        assertThat(first.missionCompleted()).isFalse();
        assertThat(first.xpGained()).isEqualTo(5);
        SessionCompletion row = completions.find(mission.getId(), 2, child);
        assertThat(row.completedOn()).isEqualTo(Fixed.TODAY);
        assertThat(row.completedAt()).isEqualTo(Fixed.NOW);
        assertThat(row.activeSeconds()).isEqualTo(100);
        assertThat(videoSecondsOf(child)).isEqualTo(100);
        MissionParticipantView partly = viewOf(mission, child);
        assertThat(partly.doneSessions()).containsExactly(2);
        assertThat(partly.verifiedBy()).isEqualTo(VerifiedBy.VIDEO_PROGRESS); // 첫 칸부터 확인 방법이 실린다
        assertThat(partly.completed()).isFalse();

        assertThat(complete(family.childUser, mission, 1, child, 40).xpGained()).isEqualTo(5);
        SessionCompletedView last = complete(family.childUser, mission, 3, child, 60);

        assertThat(last.missionProgress()).isEqualTo(1.0);
        assertThat(last.missionCompleted()).isTrue();
        assertThat(last.xpGained()).isEqualTo(25);
        assertThat(viewOf(mission, child).doneSessions()).containsExactly(1, 2, 3);
        assertThat(videoSecondsOf(child)).isEqualTo(200);
        // 경험치는 활동을 쌓은 뒤에 부른다 — 누적 분 업적이 이번 칸까지 센다
        assertThat(progress.minutesAtCall).containsExactly(1, 2, 3);
        assertThat(events).filteredOn(SessionCompleted.class::isInstance).hasSize(3);
        assertThat(events)
                .filteredOn(MissionCompleted.class::isInstance)
                .containsExactly(new MissionCompleted(mission.getId(), child, family.familyId, Fixed.NOW));
        assertThat(events.getLast())
                .isEqualTo(new MissionCompleted(mission.getId(), child, family.familyId, Fixed.NOW));
        assertThat(events.get(events.size() - 2))
                .isEqualTo(
                        new SessionCompleted(family.familyId, mission.getId(), 3, child, Fixed.TODAY, Fixed.NOW, true));
    }

    @Test
    @DisplayName("같은 칸을 다시 보내면 200 · xpGained 0 — 행 · 활동 · 끝낸 날이 그대로다(다음 날 다시 보내도 옮기지 않는다)")
    void 같은_칸을_다시_보내면_멱등이다() {
        Mission mission = missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "이틀짜리",
                TargetMetric.TIMER_MINUTES,
                2,
                null,
                Fixed.TODAY,
                Fixed.TODAY.plusDays(1),
                List.of(child),
                List.of(session(1, SessionPhase.MAIN, 1), session(2, SessionPhase.MAIN, 1)),
                parent,
                Fixed.NOW));
        complete(family.childUser, mission, 1, child, 60);
        int eventsBefore = events.size();

        SessionCompletedView again = service(Fixed.NOW.plusSeconds(24 * 3600))
                .complete(family.childUser, mission.getId(), 1, played(child, 60));

        assertThat(again.xpGained()).isZero();
        assertThat(again.missionProgress()).isEqualTo(0.5);
        assertThat(again.verifiedBy()).isEqualTo(VerifiedBy.VIDEO_PROGRESS);
        assertThat(completions.rows).hasSize(1);
        assertThat(completions.find(mission.getId(), 1, child).completedOn()).isEqualTo(Fixed.TODAY);
        assertThat(videoSecondsOf(child)).isEqualTo(60);
        assertThat(progress.calls).hasSize(1);
        assertThat(events).hasSize(eventsBefore);
    }

    @Test
    @DisplayName("아이가 끝낸 코치 미션 칸은 동반자 보호자에게 번진다 — 응원만 하는 부모에게는 번지지 않고, 응답 경험치는 아이 몫만")
    void 코치_미션은_동반자에게만_번진다() {
        Mission mission = coach();

        SessionCompletedView view = complete(family.childUser, mission, 1, child, 120);

        assertThat(view.xpGained()).isEqualTo(25);
        assertThat(completions.of(parent))
                .extracting(SessionCompletion::position)
                .containsExactly(1);
        assertThat(completions.of(cheerParent)).isEmpty();
        assertThat(videoSecondsOf(parent)).isEqualTo(120);
        assertThat(videoSecondsOf(cheerParent)).isZero();
        assertThat(progress.calls).extracting(SessionDone::profileId).containsExactly(child, parent);
        assertThat(viewOf(mission, parent).completed()).isTrue();
        assertThat(viewOf(mission, parent).doneSessions()).containsExactly(1);
        assertThat(viewOf(mission, cheerParent).doneSessions()).isEmpty();
        assertThat(events)
                .filteredOn(MissionCompleted.class::isInstance)
                .extracting(it -> ((MissionCompleted) it).profileId())
                .containsExactly(child, parent);
    }

    @Test
    @DisplayName("직접 짜기 미션은 보호자 참여자 전원에게 번지고 형제에게는 번지지 않는다. 보호자가 끝낸 칸은 그 보호자 것뿐이다")
    void 직접_짜기는_보호자_전원에게_번지고_형제와_보호자_칸은_번지지_않는다() {
        ProfileDetails sibling = family.addChild("은영", LocalDate.of(2017, 4, 1));
        Mission mission = manual(List.of(child, sibling.profileId(), parent, cheerParent));

        complete(family.childUser, mission, 1, child, 60);

        assertThat(completions.of(parent)).hasSize(1);
        assertThat(completions.of(cheerParent)).hasSize(1);
        assertThat(completions.of(sibling.profileId())).isEmpty();

        complete(family.parentUser, mission, 2, parent, 120);

        assertThat(completions.of(parent))
                .extracting(SessionCompletion::position)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(completions.of(child))
                .extracting(SessionCompletion::position)
                .containsExactly(1);
        assertThat(completions.of(cheerParent))
                .extracting(SessionCompletion::position)
                .containsExactly(1);
        // 형제가 끝낸 칸은 다른 아이에게 번지지 않는다(부모 계정이 계정 없는 아이 이름으로 보낸다)
        complete(family.parentUser, mission, 3, sibling.profileId(), 60);
        assertThat(completions.of(sibling.profileId()))
                .extracting(SessionCompletion::position)
                .containsExactly(3);
        assertThat(completions.of(child))
                .extracting(SessionCompletion::position)
                .containsExactly(1);
    }

    @Test
    @DisplayName("보호자가 먼저 스스로 끝낸 칸은 아이가 끝내도 다시 적지 않는다 — 그 보호자의 끝낸 날 · 활동이 그대로다")
    void 보호자가_먼저_끝낸_칸은_다시_적지_않는다() {
        Mission mission = manual(List.of(child, parent));
        complete(family.parentUser, mission, 1, parent, 60);

        complete(family.childUser, mission, 1, child, 60);

        assertThat(completions.of(parent)).hasSize(1);
        assertThat(videoSecondsOf(parent)).isEqualTo(60);
        assertThat(progress.calls).extracting(SessionDone::profileId).containsExactly(parent, child);
    }

    @Test
    @DisplayName("판정 차례: 미션 404 → 칸 404 → 대신 보낼 수 없음 403 → 참여자 아님 403 → 동의 422 → 기간 밖 422 → 시각 400 → 절반 미만 422")
    void 판정_차례() {
        Mission mission = manual(List.of(child));
        UUID missionId = mission.getId();

        assertThat(assertThrows(
                                MissionNotFoundException.class,
                                () -> service.complete(family.childUser, UUID.randomUUID(), 1, played(child, 60)))
                        .getCode())
                .isEqualTo("MISSION_NOT_FOUND");
        for (int position : new int[] {0, 4}) {
            assertThat(assertThrows(
                                    SessionNotFoundException.class,
                                    () -> service.complete(family.childUser, missionId, position, played(child, 60)))
                            .getCode())
                    .isEqualTo("SESSION_NOT_FOUND");
        }
        // 계정 있는 아이 이름으로 부모가 보내거나, 아이가 부모 이름으로 보낼 수 없다
        assertThrows(
                CannotActAsProfileException.class,
                () -> service.complete(family.parentUser, missionId, 1, played(child, 60)));
        assertThrows(
                CannotActAsProfileException.class,
                () -> service.complete(family.childUser, missionId, 1, played(parent, 60)));
        NotParticipantException notParticipant = assertThrows(
                NotParticipantException.class,
                () -> service.complete(family.parentUser, missionId, 1, played(parent, 60)));
        assertThat(notParticipant.getCode()).isEqualTo("NOT_A_PARTICIPANT");

        Mission tomorrow = manual(List.of(child), Fixed.TODAY.plusDays(1), Fixed.TODAY.plusDays(1));
        Mission yesterday = manual(List.of(child), Fixed.TODAY.minusDays(1), Fixed.TODAY.minusDays(1));
        for (Mission outside : List.of(tomorrow, yesterday)) {
            assertThat(assertThrows(
                                    MissionNotActiveException.class,
                                    () -> complete(family.childUser, outside, 1, child, 60))
                            .getCode())
                    .isEqualTo("MISSION_NOT_ACTIVE");
        }
        Instant at = Fixed.NOW.minusSeconds(60);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.complete(family.childUser, missionId, 1, new CompleteSessionCommand(child, 60, at, at)));
        // 칸 1분의 절반은 30초. 재생 29초는 모자라고, 재생 600초라도 기기 시각 간격이 20초면 20초로 잘려 모자란다
        assertThat(assertThrows(SessionTooShortException.class, () -> complete(family.childUser, mission, 1, child, 29))
                        .getCode())
                .isEqualTo("TOO_SHORT");
        assertThrows(
                SessionTooShortException.class,
                () -> service.complete(
                        family.childUser,
                        missionId,
                        1,
                        new CompleteSessionCommand(child, 600, at, at.plusSeconds(20))));
        assertThat(complete(family.childUser, mission, 1, child, 30).xpGained()).isEqualTo(5);

        family.withdrawConsent(child);
        assertThat(assertThrows(
                                ParticipantConsentRequiredException.class,
                                () -> complete(family.childUser, mission, 2, child, 120))
                        .getCode())
                .isEqualTo("CONSENT_REQUIRED");
        assertThat(completions.rows).hasSize(1);
    }

    @Test
    @DisplayName("기기 시각 간격이 재생 초보다 짧으면 간격으로 잘라 적는다")
    void 재생_초는_기기_시각_간격으로_자른다() {
        Mission mission = manual(List.of(child));
        Instant startedAt = Fixed.NOW.minusSeconds(100);

        service.complete(
                family.childUser, mission.getId(), 2, new CompleteSessionCommand(child, 3000, startedAt, Fixed.NOW));

        assertThat(completions.find(mission.getId(), 2, child).activeSeconds()).isEqualTo(100);
        assertThat(videoSecondsOf(child)).isEqualTo(100);
    }

    @Test
    @DisplayName("부모 계정은 계정 없는 아이 이름으로 끝낼 수 있다(아이가 부모 폰을 빌려 쓴다)")
    void 부모는_계정_없는_아이_이름으로_끝낼_수_있다() {
        ProfileDetails kid = family.addChild("은영", LocalDate.of(2017, 4, 1));
        Mission mission = manual(List.of(kid.profileId()));

        SessionCompletedView view = complete(family.parentUser, mission, 1, kid.profileId(), 60);

        assertThat(view.xpGained()).isEqualTo(5);
        assertThat(completions.of(kid.profileId())).hasSize(1);
    }

    @Test
    @DisplayName("칸 없는 미션은 1번을 미션 전체 한 칸(분 목표면 targetValue 분)으로 받아 끝내면 완료다. 다른 번호는 404")
    void 칸_없는_미션은_1번을_미션_전체로_받는다() {
        Mission timer = sessionless(TargetMetric.TIMER_MINUTES, 20);

        assertThrows(SessionTooShortException.class, () -> complete(family.childUser, timer, 1, child, 599));
        SessionCompletedView view = complete(family.childUser, timer, 1, child, 600);

        assertThat(view.missionCompleted()).isTrue();
        assertThat(view.missionProgress()).isEqualTo(1.0);
        assertThat(view.xpGained()).isEqualTo(25);
        MissionParticipantView participant = viewOf(timer, child);
        assertThat(participant.doneSessions()).containsExactly(1);
        assertThat(participant.verifiedBy()).isEqualTo(VerifiedBy.VIDEO_PROGRESS);
        assertThat(missionService.get(family.parentUser, timer.getId()).sessions())
                .isEmpty();
        assertThrows(SessionNotFoundException.class, () -> complete(family.childUser, timer, 2, child, 600));
    }

    @Test
    @DisplayName("칸 없는 걸음수 미션은 1분 칸으로 받아 기록 · 활동은 남기지만, 완료는 걸음수와 보호자 확인으로만 한다")
    void 칸_없는_걸음수_미션은_칸_끝으로_끝나지_않는다() {
        Mission steps = sessionless(TargetMetric.STEPS, 3000);

        SessionCompletedView view = complete(family.childUser, steps, 1, child, 30);

        assertThat(view.missionCompleted()).isFalse();
        assertThat(view.missionProgress()).isZero();
        assertThat(view.xpGained()).isEqualTo(5);
        assertThat(videoSecondsOf(child)).isEqualTo(30);
    }

    @Test
    @DisplayName("한 미션의 칸만 해도 같은 날 다른 미션은 끝나지 않는다 — 활동 합계가 아니라 그 미션의 끝낸 칸으로 센다(Q-live-01)")
    void 한_미션만_해도_다른_미션이_끝나지_않는다() {
        Mission done = manual(List.of(child));
        Mission other = missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "저녁 운동",
                TargetMetric.TIMER_MINUTES,
                2,
                null,
                Fixed.TODAY,
                Fixed.TODAY,
                List.of(child),
                List.of(session(1, SessionPhase.MAIN, 2)),
                parent,
                Fixed.NOW));
        for (int position = 1; position <= 3; position++) complete(family.childUser, done, position, child, 120);
        // 타이머 경로로 분을 더 쌓아도 칸 있는 미션은 끝나지 않는다
        activityService.recordTimer(
                family.childUser,
                other.getId(),
                new RecordTimerCommand(child, Fixed.NOW.minusSeconds(1200), Fixed.NOW, 20));

        assertThat(activity.totals(child, Fixed.TODAY, Fixed.TODAY).verifiedMinutes())
                .isEqualTo(26);
        MissionParticipantView untouched = viewOf(other, child);
        assertThat(untouched.progress()).isZero();
        assertThat(untouched.completed()).isFalse();
        assertThat(untouched.doneSessions()).isEmpty();
        assertThat(viewOf(done, child).completed()).isTrue();
    }
}
