package kr.ac.kookmin.familyfitness.coaching.application;

import static kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.failureOf;
import static kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.race;
import static kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.resultOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.Pause;
import kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.Race;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionFeedbackRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionAlreadyStartedException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeedback;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeel;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.progress.api.ProgressRecorder;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 미션 행 잠금(SELECT … FOR UPDATE)을 두고 보호자의 미션 지우기 · 느낌 · 칸 끝이 겹칠 때 — 실제 DB 에 커밋하며 본다.
 * H2 판과 PostgreSQL 판이 이 클래스를 잇는다. 세 요청 모두 미션 행을 맨 먼저 잠그므로 차례로 돌고, 서로를 기다리다 멈추지 않는다.
 *
 * <p>서비스는 실제 저장소 · 활동 · 경험치 빈으로 손으로 조립하고 identity 만 목으로 바꾼다. 저장소 하나를 감싸 정한 자리에서 한 요청을
 * 멈춰 세운다({@link Pause}). 멈춘 동안 그 요청의 트랜잭션 · 잠금은 그대로다. 모든 행을 커밋하므로 끝나면 이 가족의 행을 지운다.
 */
abstract class MissionDeletionRaceTestBase {
    @Autowired
    MissionRepository missions;

    @Autowired
    SessionCompletionRepository completions;

    @Autowired
    MissionFeedbackRepository feedbacks;

    @Autowired
    ActivityRecorder activity;

    @Autowired
    ProgressRecorder progress;

    @Autowired
    MissionCompletionPolicy policy;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    AppTime time;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProfileRows rows;

    final FamilyAccess familyAccess = mock(FamilyAccess.class);
    final ProfileQuery profiles = mock(ProfileQuery.class);
    final UUID momUser = UUID.randomUUID();
    UUID familyId;
    UUID momId;
    UUID kidId;
    UUID missionId;

    /** 오늘 하루짜리 직접 짜기 한 칸(본운동 1분). 아이 혼자 한다. 보호자(엄마)가 지운다. */
    @BeforeEach
    void setUp() {
        familyId = rows.family("지우기 경합");
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        ProfileSummary mom = summary(momId, ProfileRole.PARENT);
        ProfileSummary kid = summary(kidId, ProfileRole.CHILD);
        when(familyAccess.requireParent(momUser, familyId)).thenReturn(mom);
        when(familyAccess.requireActingAs(momUser, kidId)).thenReturn(kid);
        when(profiles.summariesOfFamily(familyId)).thenReturn(List.of(mom, kid));

        missionId = UUID.randomUUID();
        LocalDate today = time.today();
        tx().executeWithoutResult(status -> missions.save(Mission.manual(
                missionId,
                familyId,
                "거북이 스트레칭",
                TargetMetric.TIMER_MINUTES,
                1,
                null,
                today,
                today,
                List.of(kidId),
                List.of(new MissionSession(1, SessionPhase.MAIN, "거북이 스트레칭", FitnessFactor.FLEXIBILITY, 1, null)),
                momId,
                Instant.now())));
    }

    @AfterEach
    void tearDown() {
        for (String table : List.of(
                "mission_feedback", "mission_session_completions", "mission_participants", "mission_sessions")) {
            jdbc.update("delete from " + table + " where mission_id = ?", missionId);
        }
        jdbc.update("delete from missions where id = ?", missionId);
        for (String table : List.of("notifications", "activity_daily", "progress_xp_events", "progress_achievements")) {
            jdbc.update("delete from " + table + " where profile_id in (?, ?)", momId, kidId);
        }
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
    }

    private ProfileSummary summary(UUID profileId, ProfileRole role) {
        boolean child = role == ProfileRole.CHILD;
        return new ProfileSummary(
                profileId,
                familyId,
                child ? "서준" : "엄마",
                role,
                child ? AgeGroup.YOUTH : AgeGroup.ADULT,
                child ? Sex.M : Sex.F,
                !child,
                child ? InviteStatus.NONE : InviteStatus.CLAIMED,
                null,
                true,
                child,
                true,
                false);
    }

    @Test
    @DisplayName("지우기가 미션 행을 잡고 있는 동안 온 칸 끝은 미션을 읽다가 기다렸다가 404 — 지우기는 끝까지 가고 칸 끝 · 활동 기록이 남지 않는다")
    void 지우기가_잡고_있는_동안_온_칸_끝은_기다렸다가_404() throws InterruptedException {
        Pause pause = new Pause();
        MissionDeletionService deleting = deletion(new PausedFeedbacks(feedbacks, pause));
        SessionCompletionService completing = completion(completions);

        Race<Void, SessionCompletedView> race = race(
                pause,
                run(() -> deleting.delete(momUser, missionId)),
                call(() -> completing.complete(momUser, missionId, 1, oneMinute())));

        resultOf(race.first());
        assertThat(failureOf(race.second())).isInstanceOf(MissionNotFoundException.class);
        assertThat(rowsOf("missions", "id")).isZero();
        assertThat(rowsOf("mission_session_completions", "mission_id")).isZero();
        assertThat(activeSecondsOfKid()).isZero();
    }

    @Test
    @DisplayName("칸 끝이 먼저 미션 행을 잡았으면, 지우기는 그 트랜잭션이 끝나길 기다렸다가 409 MISSION_ALREADY_STARTED")
    void 칸_끝이_먼저_잡았으면_지우기는_기다렸다가_409() throws InterruptedException {
        Pause pause = new Pause();
        SessionCompletionService completing = completion(new HookedCompletions(completions, pause::hold));
        MissionDeletionService deleting = deletion(feedbacks);

        Race<SessionCompletedView, Void> race = race(
                pause,
                call(() -> completing.complete(momUser, missionId, 1, oneMinute())),
                run(() -> deleting.delete(momUser, missionId)));

        SessionCompletedView done = resultOf(race.first());
        assertThat(done).isNotNull();
        assertThat(done.missionCompleted()).isTrue();
        assertThat(failureOf(race.second())).isInstanceOf(MissionAlreadyStartedException.class);
        assertThat(rowsOf("missions", "id")).isOne();
        assertThat(rowsOf("mission_session_completions", "mission_id")).isOne();
    }

    @Test
    @DisplayName("지우기가 미션 행을 잡고 있는 동안 온 느낌은 기다렸다가 404 — 지우기는 끝까지 간다")
    void 지우기가_잡고_있는_동안_온_느낌은_기다렸다가_404() throws InterruptedException {
        Pause pause = new Pause();
        MissionDeletionService deleting = deletion(new PausedFeedbacks(feedbacks, pause));
        MissionFeedbackService sending = new MissionFeedbackService(missions, feedbacks, familyAccess, time);

        Race<Void, Void> race = race(
                pause,
                run(() -> deleting.delete(momUser, missionId)),
                run(() -> sending.send(momUser, missionId, new MissionFeedbackCommand(kidId, MissionFeel.GOOD))));

        resultOf(race.first());
        assertThat(failureOf(race.second())).isInstanceOf(MissionNotFoundException.class);
        assertThat(rowsOf("missions", "id")).isZero();
        assertThat(rowsOf("mission_participants", "mission_id")).isZero();
        assertThat(rowsOf("mission_feedback", "mission_id")).isZero();
    }

    @Test
    @DisplayName("칸 끝이 미션 행을 잡고 있는 동안 온 느낌은 기다렸다가 저장된다 — 서로를 기다리다 멈추지 않는다")
    void 칸_끝이_잡고_있는_동안_온_느낌은_기다렸다가_저장된다() throws InterruptedException {
        Pause pause = new Pause();
        SessionCompletionService completing = completion(new HookedCompletions(completions, pause::hold));
        MissionFeedbackService sending = new MissionFeedbackService(missions, feedbacks, familyAccess, time);

        Race<SessionCompletedView, Void> race = race(
                pause,
                call(() -> completing.complete(momUser, missionId, 1, oneMinute())),
                run(() -> sending.send(momUser, missionId, new MissionFeedbackCommand(kidId, MissionFeel.GOOD))));

        SessionCompletedView done = resultOf(race.first());
        assertThat(done).isNotNull();
        assertThat(done.missionCompleted()).isTrue();
        resultOf(race.second());
        assertThat(rowsOf("mission_session_completions", "mission_id")).isOne();
        assertThat(rowsOf("mission_feedback", "mission_id")).isOne();
    }

    @Test
    @DisplayName("느낌이 미션 행을 잡고 있는 동안 온 칸 끝은 기다렸다가 끝난다 — 서로를 기다리다 멈추지 않는다")
    void 느낌이_잡고_있는_동안_온_칸_끝은_기다렸다가_끝난다() throws InterruptedException {
        Pause pause = new Pause();
        MissionFeedbackService sending =
                new MissionFeedbackService(missions, new PausedFeedbacks(feedbacks, pause), familyAccess, time);
        SessionCompletionService completing = completion(completions);

        Race<Void, SessionCompletedView> race = race(
                pause,
                run(() -> sending.send(momUser, missionId, new MissionFeedbackCommand(kidId, MissionFeel.HARD))),
                call(() -> completing.complete(momUser, missionId, 1, oneMinute())));

        resultOf(race.first());
        SessionCompletedView done = resultOf(race.second());
        assertThat(done).isNotNull();
        assertThat(done.missionCompleted()).isTrue();
        assertThat(rowsOf("mission_feedback", "mission_id")).isOne();
        assertThat(rowsOf("mission_session_completions", "mission_id")).isOne();
    }

    SessionCompletionService completion(SessionCompletionRepository completionRepository) {
        return new SessionCompletionService(
                missions, completionRepository, familyAccess, profiles, activity, progress, policy, events, time);
    }

    MissionDeletionService deletion(MissionFeedbackRepository feedbackRepository) {
        return new MissionDeletionService(missions, completions, feedbackRepository, familyAccess, events, time);
    }

    /** 아이가 한 칸(1분)을 끝까지 봤다. 엄마 폰에서 계정 없는 아이 이름으로 보낸다. */
    CompleteSessionCommand oneMinute() {
        Instant endedAt = Instant.now();
        return new CompleteSessionCommand(kidId, 60, endedAt.minusSeconds(120), endedAt);
    }

    TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    /** 한 트랜잭션에서 돌리고 커밋한다. */
    Callable<Void> run(Runnable work) {
        return () -> {
            tx().executeWithoutResult(status -> work.run());
            return null;
        };
    }

    <T> Callable<T> call(Supplier<T> work) {
        return () -> tx().execute(status -> work.get());
    }

    int rowsOf(String table, String missionColumn) {
        Integer count = jdbc.queryForObject(
                "select count(*) from " + table + " where " + missionColumn + " = ?", Integer.class, missionId);
        return count == null ? 0 : count;
    }

    int activeSecondsOfKid() {
        Integer seconds = jdbc.queryForObject(
                "select coalesce(sum(active_seconds), 0) from activity_daily where profile_id = ?",
                Integer.class,
                kidId);
        return seconds == null ? 0 : seconds;
    }

    /** 칸 끝 저장소를 감싸 칸 끝 행을 넣은 바로 뒤에 할 일을 끼운다. 이때 칸 끝은 미션 행을 잠근 채다. */
    record HookedCompletions(SessionCompletionRepository delegate, Runnable afterInsert)
            implements SessionCompletionRepository {
        @Override
        public @Nullable SessionCompletion find(UUID missionId, int position, UUID profileId) {
            return delegate.find(missionId, position, profileId);
        }

        @Override
        public void insert(SessionCompletion completion) {
            delegate.insert(completion);
            afterInsert.run();
        }

        @Override
        public List<SessionCompletion> findByMission(UUID missionId) {
            return delegate.findByMission(missionId);
        }

        @Override
        public List<SessionCompletion> findByMissions(Collection<UUID> missionIds) {
            return delegate.findByMissions(missionIds);
        }
    }

    /**
     * 느낌 저장소를 감싸 미션 행을 잠근 채 멈춘다 — 지우기는 느낌을 지운 뒤 · 참여자를 지우기 전에, 느낌 보내기는 느낌을 넣은 뒤에.
     * 느낌이 잠그지 않던 때는 지우기의 이 틈에 느낌이 들어가 커밋되고, 풀린 지우기가 참여자 행에서 외래 키에 걸렸다.
     */
    record PausedFeedbacks(MissionFeedbackRepository delegate, Pause pause) implements MissionFeedbackRepository {
        @Override
        public void upsert(MissionFeedback feedback) {
            delegate.upsert(feedback);
            pause.hold();
        }

        @Override
        public @Nullable MissionFeedback find(UUID missionId, UUID profileId) {
            return delegate.find(missionId, profileId);
        }

        @Override
        public void deleteByMission(UUID missionId) {
            delegate.deleteByMission(missionId);
            pause.hold();
        }
    }
}
