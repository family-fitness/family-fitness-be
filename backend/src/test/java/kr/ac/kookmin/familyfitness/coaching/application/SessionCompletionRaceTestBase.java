package kr.ac.kookmin.familyfitness.coaching.application;

import static kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.race;
import static kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.resultOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCompleted;
import kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.Pause;
import kr.ac.kookmin.familyfitness.coaching.application.RaceSteps.Race;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.progress.api.ProgressRecorder;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.assertj.core.api.SoftAssertions;
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
 * 같은 미션의 서로 다른 칸을 두 아이가 동시에 끝낼 때(SA-10) — 실제 DB 에 커밋하며 본다. H2 판과 PostgreSQL 판이 이 클래스를 잇는다.
 *
 * <p>엄마 · 첫째 · 둘째가 함께하는 두 칸짜리 직접 짜기 미션이다. 아이가 끝낸 칸은 같이 하는 엄마에게도 번진다(결정 34). 첫째의 1번 칸
 * 요청을 경험치 적립 바로 앞에서 멈춰 세우고(칸 끝 행을 넣고 진행도를 셈한 뒤다), 그 사이 둘째가 2번 칸을 끝낸다.
 *
 * <p>칸 끝이 미션 행을 잠그지 않던 때는 두 요청이 서로의 칸 끝 행을 못 본 채 엄마 진행도를 0.5 로 셈해 덮어썼다. 엄마는 두 칸을 다
 * 했는데 끝나지 않았고 MissionCompleted · 미션 끝 +20 도 없었다. 같은 날 엄마 활동 행도 두 요청이 같은 값을 읽어 고쳐 써 한 칸 분을
 * 잃었다. 잠그면 둘째는 첫째가 커밋하길 기다렸다가 두 칸을 다 보고 셈한다.
 */
abstract class SessionCompletionRaceTestBase {
    /** 엄마가 오늘 이미 움직인 초. 활동 행이 이미 있어야 두 요청이 같은 행을 고쳐 쓴다(QA 재현과 같은 조건). */
    private static final int MOM_EARLIER_SECONDS = 30;

    @Autowired
    MissionRepository missions;

    @Autowired
    SessionCompletionRepository completions;

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
    final List<Object> published = new CopyOnWriteArrayList<>();
    UUID familyId;
    UUID momId;
    UUID firstId;
    UUID secondId;
    UUID missionId;

    /** 오늘 하루짜리 직접 짜기 두 칸(본운동 1분씩). 엄마 · 첫째 · 둘째가 같이 한다. 아이들은 엄마 폰으로 보낸다(계정 없음). */
    @BeforeEach
    void setUp() {
        familyId = rows.family("칸 끝 경합");
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        firstId = rows.profile(familyId, LocalDate.of(2015, 5, 1), Sex.M, ProfileRole.CHILD, "첫째");
        secondId = rows.profile(familyId, LocalDate.of(2017, 6, 1), Sex.F, ProfileRole.CHILD, "둘째");
        ProfileSummary mom = summary(momId, ProfileRole.PARENT, "엄마");
        ProfileSummary first = summary(firstId, ProfileRole.CHILD, "첫째");
        ProfileSummary second = summary(secondId, ProfileRole.CHILD, "둘째");
        when(familyAccess.requireActingAs(momUser, firstId)).thenReturn(first);
        when(familyAccess.requireActingAs(momUser, secondId)).thenReturn(second);
        when(profiles.summariesOfFamily(familyId)).thenReturn(List.of(mom, first, second));

        missionId = UUID.randomUUID();
        LocalDate today = time.today();
        tx().executeWithoutResult(status -> {
            missions.save(Mission.manual(
                    missionId,
                    familyId,
                    "거북이 스트레칭",
                    TargetMetric.TIMER_MINUTES,
                    2,
                    null,
                    today,
                    today,
                    List.of(firstId, secondId, momId),
                    List.of(
                            new MissionSession(1, SessionPhase.MAIN, "거북이 스트레칭", FitnessFactor.FLEXIBILITY, 1, null),
                            new MissionSession(2, SessionPhase.MAIN, "고양이 스트레칭", FitnessFactor.FLEXIBILITY, 1, null)),
                    momId,
                    Instant.now()));
            activity.addActiveSeconds(momId, today, ActivitySource.VIDEO, MOM_EARLIER_SECONDS);
        });
    }

    @AfterEach
    void tearDown() {
        for (String table : List.of(
                "mission_feedback", "mission_session_completions", "mission_participants", "mission_sessions")) {
            jdbc.update("delete from " + table + " where mission_id = ?", missionId);
        }
        jdbc.update("delete from missions where id = ?", missionId);
        for (String table : List.of("notifications", "activity_daily", "progress_xp_events", "progress_achievements")) {
            jdbc.update("delete from " + table + " where profile_id in (?, ?, ?)", momId, firstId, secondId);
        }
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
    }

    private ProfileSummary summary(UUID profileId, ProfileRole role, String name) {
        boolean child = role == ProfileRole.CHILD;
        return new ProfileSummary(
                profileId,
                familyId,
                name,
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
    @DisplayName("두 아이가 서로 다른 칸을 동시에 끝내도 두 칸을 다 한 엄마는 끝나고 MissionCompleted · 미션 끝 +20 을 받는다. 엄마 활동 초도 두 칸 다 쌓인다")
    void 서로_다른_칸을_동시에_끝내도_엄마는_끝나고_미션_끝_경험치를_받는다() throws InterruptedException {
        Pause pause = new Pause();
        SessionCompletionService firstKid = completion(new PausedProgress(progress, pause, firstId));
        SessionCompletionService secondKid = completion(progress);

        Race<SessionCompletedView, SessionCompletedView> race = race(
                pause,
                call(() -> firstKid.complete(momUser, missionId, 1, oneMinute(firstId))),
                call(() -> secondKid.complete(momUser, missionId, 2, oneMinute(secondId))));

        resultOf(race.first());
        resultOf(race.second());
        long momCompletedEvents = published.stream()
                .filter(MissionCompleted.class::isInstance)
                .map(it -> ((MissionCompleted) it).profileId())
                .filter(momId::equals)
                .count();
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(statusOf(momId)).as("엄마 참여 상태").isEqualTo("COMPLETED");
            soft.assertThat(progressOf(momId)).as("엄마 진행도").isEqualTo(1.0);
            soft.assertThat(xpOf(momId)).as("엄마 경험치(칸 +5 · 칸 +5 · 미션 끝 +20)").isEqualTo(5 + 5 + 20);
            soft.assertThat(momCompletedEvents).as("엄마 MissionCompleted 수").isOne();
            soft.assertThat(activeSecondsOf(momId))
                    .as("엄마 활동 초(먼저 움직인 30초 + 두 칸)")
                    .isEqualTo(MOM_EARLIER_SECONDS + 60 + 60);
        });
    }

    SessionCompletionService completion(ProgressRecorder progressRecorder) {
        ApplicationEventPublisher recording = event -> {
            published.add(event);
            events.publishEvent(event);
        };
        return new SessionCompletionService(
                missions, completions, familyAccess, profiles, activity, progressRecorder, policy, recording, time);
    }

    /** 그 아이가 한 칸(1분)을 끝까지 봤다. 엄마 폰에서 계정 없는 아이 이름으로 보낸다. */
    CompleteSessionCommand oneMinute(UUID kidId) {
        Instant endedAt = Instant.now();
        return new CompleteSessionCommand(kidId, 60, endedAt.minusSeconds(120), endedAt);
    }

    TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    <T> Callable<T> call(Supplier<T> work) {
        return () -> tx().execute(status -> work.get());
    }

    String statusOf(UUID profileId) {
        return jdbc.queryForObject(
                "select status from mission_participants where mission_id = ? and profile_id = ?",
                String.class,
                missionId,
                profileId);
    }

    double progressOf(UUID profileId) {
        Double value = jdbc.queryForObject(
                "select progress from mission_participants where mission_id = ? and profile_id = ?",
                Double.class,
                missionId,
                profileId);
        return value == null ? 0 : value;
    }

    int xpOf(UUID profileId) {
        Integer xp = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from progress_xp_events where profile_id = ?",
                Integer.class,
                profileId);
        return xp == null ? 0 : xp;
    }

    int activeSecondsOf(UUID profileId) {
        Integer seconds = jdbc.queryForObject(
                "select coalesce(sum(active_seconds), 0) from activity_daily where profile_id = ?",
                Integer.class,
                profileId);
        return seconds == null ? 0 : seconds;
    }

    /**
     * 경험치 적립을 감싸, 그 아이 몫을 적립하기 바로 앞에서 멈춘다. 이때 칸 끝은 칸 끝 행을 넣고 진행도를 셈해 둔 채다(아직 커밋 전).
     */
    record PausedProgress(ProgressRecorder delegate, Pause pause, UUID holdBefore) implements ProgressRecorder {
        @Override
        public int sessionDone(SessionDone done) {
            if (done.profileId().equals(holdBefore)) pause.hold();
            return delegate.sessionDone(done);
        }
    }
}
