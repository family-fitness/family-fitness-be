package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidMetricException;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.NotParticipantException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetNotReachedException;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MissionServiceTest {
    private final Family family = new Family();
    private final Family other = new Family();
    private final FakeIdentity identity = new FakeIdentity(family, other);
    private final FakeActivity activity = new FakeActivity();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository();
    private final InMemoryVideoInteractionRepository interactions = new InMemoryVideoInteractionRepository();
    private final InMemoryExerciseVideoRepository videos = new InMemoryExerciseVideoRepository(Videos.seed());
    private final MissionCompletionPolicy policy = new MissionCompletionPolicy(activity, interactions, missions);
    private final MissionService service =
            new MissionService(missions, videos, identity, identity, policy, Fixed.time());
    private final MissionActivityService activityService =
            new MissionActivityService(missions, identity, activity, activity, policy, Fixed.time());

    private MissionCreatedView create() {
        return create(TargetMetric.TIMER_MINUTES, 45, List.of(family.child.profileId()), null);
    }

    private MissionCreatedView create(TargetMetric metric, int target) {
        return create(metric, target, List.of(family.child.profileId()), null);
    }

    private MissionCreatedView create(
            TargetMetric metric, int target, List<UUID> participants, @Nullable String videoId) {
        return service.create(
                family.parentUser,
                family.familyId,
                new CreateMissionCommand(
                        "함께 운동",
                        Fixed.WEEK_START,
                        Fixed.WEEK_START.plusDays(6),
                        metric,
                        target,
                        videoId,
                        participants));
    }

    @Test
    @DisplayName("부모는 같은 가족 참여자로 미션을 직접 만든다")
    void 부모는_같은_가족_참여자로_미션을_직접_만든다() {
        MissionCreatedView created =
                create(TargetMetric.TIMER_MINUTES, 45, List.of(family.child.profileId()), "IdpXx2gm90o");

        assertThat(created.origin()).isEqualTo(MissionOrigin.MANUAL);
        assertThat(created.coachRunId()).isNull();
        assertThat(created.serverVerifiable()).isTrue();
        Mission mission = missions.findById(created.missionId());
        assertThat(mission.getCreatedBy()).isEqualTo(family.parent.profileId());
        assertThat(mission.getVideo().videoId()).isEqualTo("IdpXx2gm90o");
        assertThat(mission.getParticipants())
                .singleElement()
                .extracting(MissionParticipant::getProfileId)
                .isEqualTo(family.child.profileId());
    }

    @Test
    @DisplayName("자녀·다른 가족 참여자·없는 영상은 거부된다")
    void 자녀_다른_가족_참여자_없는_영상은_거부된다() {
        assertThrows(
                NotAParentException.class,
                () -> service.create(
                        family.childUser,
                        family.familyId,
                        new CreateMissionCommand(
                                "t",
                                Fixed.TODAY,
                                Fixed.TODAY,
                                TargetMetric.STEPS,
                                1,
                                null,
                                List.of(family.child.profileId()))));
        assertThat(assertThrows(
                                NotFamilyMemberException.class,
                                () -> create(TargetMetric.TIMER_MINUTES, 45, List.of(other.child.profileId()), null))
                        .getCode())
                .isEqualTo("NOT_FAMILY_MEMBER");
        assertThat(assertThrows(
                                VideoNotFoundException.class,
                                () -> create(TargetMetric.TIMER_MINUTES, 45, List.of(family.child.profileId()), "nope"))
                        .getCode())
                .isEqualTo("VIDEO_NOT_FOUND");
        assertThat(create(TargetMetric.STEPS, 3000).serverVerifiable()).isFalse();
    }

    @Test
    @DisplayName("타이머 기록은 경과 시간으로 자르고 진행도를 갱신하며 목표에 닿으면 완료된다")
    void 타이머_기록은_경과_시간으로_자르고_진행도를_갱신하며_목표에_닿으면_완료된다() {
        UUID missionId = create(TargetMetric.TIMER_MINUTES, 30).missionId();
        Instant startedAt = Instant.parse("2026-09-08T23:30:00Z"); // KST 9/9 08:30

        TimerRecordedView first = activityService.recordTimer(
                family.childUser,
                missionId,
                new RecordTimerCommand(family.child.profileId(), startedAt, startedAt.plusSeconds(20 * 60), 60));
        assertThat(first.activityDate()).isEqualTo(Fixed.TODAY);
        assertThat(first.source()).isEqualTo(ActivitySource.TIMER);
        assertThat(first.serverVerified()).isTrue();
        assertThat(first.totalActiveMinutes()).isEqualTo(20);
        assertThat(first.missionProgress()).isCloseTo(0.666, Offset.offset(0.001));
        assertThat(first.missionCompleted()).isFalse();

        TimerRecordedView second = activityService.recordTimer(
                family.parentUser,
                missionId,
                new RecordTimerCommand(
                        family.child.profileId(),
                        startedAt.plusSeconds(3600),
                        startedAt.plusSeconds(3600 + 15 * 60),
                        10));
        assertThat(second.totalActiveMinutes()).isEqualTo(30);
        assertThat(second.missionProgress()).isEqualTo(1.0);
        assertThat(second.missionCompleted()).isTrue();
        assertThat(missions.findById(missionId)
                        .participantOf(family.child.profileId())
                        .getVerifiedBy())
                .isEqualTo(VerifiedBy.TIMER);
    }

    @Test
    @DisplayName("걸음수는 그날 총량으로 덮어쓰고 도달해도 보호자 확인이 남는다")
    void 걸음수는_그날_총량으로_덮어쓰고_도달해도_보호자_확인이_남는다() {
        UUID missionId = create(TargetMetric.STEPS, 5000).missionId();

        activityService.recordSteps(
                family.childUser, missionId, new RecordStepsCommand(family.child.profileId(), Fixed.TODAY, 3000));
        StepsRecordedView view = activityService.recordSteps(
                family.childUser, missionId, new RecordStepsCommand(family.child.profileId(), Fixed.TODAY, 6000));

        assertThat(view.source()).isEqualTo(ActivitySource.MANUAL);
        assertThat(view.serverVerified()).isFalse();
        assertThat(view.verifiedBy()).isEqualTo(VerifiedBy.SELF_REPORT);
        assertThat(view.missionProgress()).isEqualTo(1.0);
        assertThat(view.missionCompleted()).isFalse();
        assertThat(view.needsGuardianCheck()).isTrue();
        assertThat(activity.totals(family.child.profileId(), Fixed.WEEK_START, Fixed.WEEK_START.plusDays(6))
                        .steps())
                .isEqualTo(6000);

        ConfirmParticipantView confirmed = service.confirm(family.parentUser, missionId, family.child.profileId());
        assertThat(confirmed.completed()).isTrue();
        assertThat(confirmed.verifiedBy()).isEqualTo(VerifiedBy.SELF_REPORT);
        assertThat(confirmed.confirmedBy()).isEqualTo(family.parent.profileId());
        assertThat(confirmed.verifiedAt()).isEqualTo(Fixed.NOW);
    }

    @Test
    @DisplayName("활동 기록의 규칙 위반은 코드로 구분된다")
    void 활동_기록의_규칙_위반은_코드로_구분된다() {
        UUID steps = create(TargetMetric.STEPS, 5000).missionId();
        UUID timer = create(TargetMetric.TIMER_MINUTES, 30).missionId();
        Instant now = Fixed.NOW;

        assertThat(assertThrows(
                                NotParticipantException.class,
                                () -> activityService.recordSteps(
                                        family.parentUser,
                                        steps,
                                        new RecordStepsCommand(family.parent.profileId(), Fixed.TODAY, 100)))
                        .getCode())
                .isEqualTo("NOT_PARTICIPANT");
        assertThat(assertThrows(
                                InvalidMetricException.class,
                                () -> activityService.recordSteps(
                                        family.parentUser,
                                        timer,
                                        new RecordStepsCommand(family.child.profileId(), Fixed.TODAY, 100)))
                        .getCode())
                .isEqualTo("INVALID_METRIC");
        assertThat(assertThrows(
                                InvalidMetricException.class,
                                () -> activityService.recordTimer(
                                        family.parentUser,
                                        steps,
                                        new RecordTimerCommand(family.child.profileId(), now, now.plusSeconds(600), 5)))
                        .getCode())
                .isEqualTo("INVALID_METRIC");
        assertThrows(
                NotSameFamilyException.class,
                () -> activityService.recordSteps(
                        other.parentUser, steps, new RecordStepsCommand(family.child.profileId(), Fixed.TODAY, 100)));
        assertThrows(
                IllegalArgumentException.class,
                () -> activityService.recordSteps(
                        family.parentUser,
                        steps,
                        new RecordStepsCommand(family.child.profileId(), Fixed.TODAY.plusDays(1), 100)));
        assertThrows(
                IllegalArgumentException.class,
                () -> activityService.recordTimer(
                        family.parentUser, timer, new RecordTimerCommand(family.child.profileId(), now, now, 5)));
        assertThat(assertThrows(
                                TargetNotReachedException.class,
                                () -> service.confirm(family.parentUser, steps, family.child.profileId()))
                        .getCode())
                .isEqualTo("TARGET_NOT_REACHED");
        assertThrows(
                NotAParentException.class, () -> service.confirm(family.childUser, steps, family.child.profileId()));
    }

    @Test
    @DisplayName("보호자 동의를 거둔 아이의 타이머 · 걸음수 기록은 422 CONSENT_REQUIRED 이고 활동이 쌓이지 않는다")
    void 보호자_동의를_거둔_아이의_타이머_걸음수_기록은_CONSENT_REQUIRED() {
        UUID timer = create(TargetMetric.TIMER_MINUTES, 30).missionId();
        UUID steps = create(TargetMetric.STEPS, 5000).missionId();
        family.withdrawConsent(family.child.profileId());
        Instant startedAt = Instant.parse("2026-09-08T23:30:00Z");

        assertThat(assertThrows(
                                ParticipantConsentRequiredException.class,
                                () -> activityService.recordTimer(
                                        family.childUser,
                                        timer,
                                        new RecordTimerCommand(
                                                family.child.profileId(), startedAt, startedAt.plusSeconds(1200), 20)))
                        .getCode())
                .isEqualTo("CONSENT_REQUIRED");
        assertThat(assertThrows(
                                ParticipantConsentRequiredException.class,
                                () -> activityService.recordSteps(
                                        family.parentUser,
                                        steps,
                                        new RecordStepsCommand(family.child.profileId(), Fixed.TODAY, 3000)))
                        .getCode())
                .isEqualTo("CONSENT_REQUIRED");
        assertThat(activity.rows).isEmpty();
    }

    @Test
    @DisplayName("목록은 scope·status 로 거르고 읽을 때 진행도를 다시 계산한다")
    void 목록은_scope_status_로_거르고_읽을_때_진행도를_다시_계산한다() {
        UUID mine = create(TargetMetric.TIMER_MINUTES, 30, List.of(family.child.profileId()), null)
                .missionId();
        UUID familyWide = create(
                        TargetMetric.STEPS, 100, List.of(family.child.profileId(), family.parent.profileId()), null)
                .missionId();
        UUID expired = service.create(
                        family.parentUser,
                        family.familyId,
                        new CreateMissionCommand(
                                "지난 미션",
                                Fixed.WEEK_START.minusDays(7),
                                Fixed.WEEK_START.minusDays(1),
                                TargetMetric.STEPS,
                                100,
                                null,
                                List.of(family.child.profileId())))
                .missionId();
        activity.addActiveMinutes(family.child.profileId(), Fixed.TODAY, ActivitySource.VIDEO, 30);

        MissionListView all = service.list(family.childUser, family.familyId, MissionScope.ALL, null);
        assertThat(all.missions().stream().map(MissionView::missionId).toList())
                .containsExactlyInAnyOrder(mine, familyWide, expired);
        MissionView mineView = all.missions().stream()
                .filter(it -> it.missionId().equals(mine))
                .findFirst()
                .orElseThrow();
        assertThat(mineView.participants()).hasSize(1);
        assertThat(mineView.participants().getFirst().progress()).isEqualTo(1.0);
        assertThat(mineView.participants().getFirst().completed()).isTrue();
        assertThat(mineView.participants().getFirst().name()).isEqualTo("민준");
        assertThat(mineView.serverVerifiable()).isTrue();

        assertThat(missionIds(service.list(family.childUser, family.familyId, MissionScope.MINE, null)))
                .containsExactlyInAnyOrder(mine, familyWide, expired);
        assertThat(missionIds(service.list(family.parentUser, family.familyId, MissionScope.MINE, null)))
                .containsExactly(familyWide);
        assertThat(missionIds(service.list(family.parentUser, family.familyId, MissionScope.FAMILY, null)))
                .containsExactly(familyWide);
        assertThat(missionIds(service.list(family.parentUser, family.familyId, MissionScope.ALL, MissionStatus.DONE)))
                .containsExactly(mine);
        assertThat(missionIds(
                        service.list(family.parentUser, family.familyId, MissionScope.ALL, MissionStatus.EXPIRED)))
                .containsExactly(expired);
        assertThat(missionIds(service.list(family.parentUser, family.familyId, MissionScope.ALL, MissionStatus.ACTIVE)))
                .containsExactly(familyWide);
        assertThrows(
                NotSameFamilyException.class,
                () -> service.list(other.parentUser, family.familyId, MissionScope.ALL, null));
    }

    @Test
    @DisplayName("칸을 담아 만들면 보낸 차례 그대로 저장하고 목표 분은 칸 시간의 합이며 목록과 단건이 같은 칸을 준다")
    void 칸을_담아_만들면_보낸_차례_그대로_저장하고_목표_분은_칸_시간의_합이다() {
        // 정리운동을 1번, 준비운동을 2번에 담았다 — 단계로 다시 세우지 않는다.
        // -EATykJOvBQ 는 카탈로그(Videos.seed)에 없는 영상이지만 칸은 사본이라 받는다.
        UUID missionId = service.create(
                        family.parentUser,
                        family.familyId,
                        new CreateMissionCommand(
                                "거북이 스트레칭",
                                Fixed.TODAY,
                                Fixed.TODAY,
                                TargetMetric.TIMER_MINUTES,
                                5,
                                null,
                                List.of(family.child.profileId()),
                                List.of(
                                        new MissionSession(
                                                1,
                                                SessionPhase.COOLDOWN,
                                                "거북이 스트레칭",
                                                FitnessFactor.FLEXIBILITY,
                                                2,
                                                new SessionClip("-EATykJOvBQ", 6, 78, "거북이 스트레칭")),
                                        new MissionSession(
                                                2,
                                                SessionPhase.WARMUP,
                                                "제자리 걷기",
                                                null,
                                                3,
                                                new SessionClip("IdpXx2gm90o", 96, 150, null)))))
                .missionId();

        MissionView one = service.get(family.childUser, missionId);
        assertThat(one.targetMetric()).isEqualTo(TargetMetric.TIMER_MINUTES);
        assertThat(one.targetValue()).isEqualTo(5);
        assertThat(one.sessions())
                .containsExactly(
                        new MissionSessionView(
                                1,
                                SessionPhase.COOLDOWN,
                                "거북이 스트레칭",
                                FitnessFactor.FLEXIBILITY,
                                2,
                                new SessionClipView("-EATykJOvBQ", 6, 78, "거북이 스트레칭")),
                        new MissionSessionView(
                                2,
                                SessionPhase.WARMUP,
                                "제자리 걷기",
                                null,
                                3,
                                new SessionClipView("IdpXx2gm90o", 96, 150, null)));
        assertThat(service.list(family.parentUser, family.familyId, MissionScope.ALL, null)
                        .missions())
                .containsExactly(one);
    }

    @Test
    @DisplayName("동의가 필요한데 없는 참여자가 끼면 CONSENT_REQUIRED 로 막고 미션을 만들지 않는다")
    void 동의가_필요한데_없는_참여자가_끼면_CONSENT_REQUIRED_로_막는다() {
        FakeIdentity withdrawn = new FakeIdentity(family) {
            @Override
            public List<ProfileSummary> summariesOfFamily(UUID familyId) {
                return super.summariesOfFamily(familyId).stream()
                        .map(it -> it.profileId().equals(family.child.profileId()) ? consentWithdrawn(it) : it)
                        .toList();
            }
        };
        MissionService guarded = new MissionService(missions, videos, withdrawn, withdrawn, policy, Fixed.time());
        CreateMissionCommand withChild = new CreateMissionCommand(
                "함께 운동",
                Fixed.TODAY,
                Fixed.TODAY,
                TargetMetric.TIMER_MINUTES,
                10,
                null,
                List.of(family.parent.profileId(), family.child.profileId()));

        ParticipantConsentRequiredException e = assertThrows(
                ParticipantConsentRequiredException.class,
                () -> guarded.create(family.parentUser, family.familyId, withChild));
        assertThat(e.getCode()).isEqualTo("CONSENT_REQUIRED");
        assertThat(e.getKind()).isEqualTo(ErrorKind.RULE_VIOLATION);
        assertThat(missions.missions).isEmpty();

        UUID parentOnly = guarded.create(
                        family.parentUser,
                        family.familyId,
                        new CreateMissionCommand(
                                "함께 운동",
                                Fixed.TODAY,
                                Fixed.TODAY,
                                TargetMetric.TIMER_MINUTES,
                                10,
                                null,
                                List.of(family.parent.profileId())))
                .missionId();
        assertThat(missions.findById(parentOnly)).isNotNull();
    }

    @Test
    @DisplayName("단건 조회는 목록과 같은 권한이고 칸 없는 미션의 sessions 는 빈 목록이다")
    void 단건_조회는_목록과_같은_권한이고_칸_없는_미션의_sessions_는_빈_목록이다() {
        UUID missionId = create().missionId();

        MissionView view = service.get(family.parentUser, missionId);
        assertThat(view.missionId()).isEqualTo(missionId);
        assertThat(view.sessions()).isEmpty();
        assertThat(view.targetValue()).isEqualTo(45);
        assertThat(assertThrows(MissionNotFoundException.class, () -> service.get(family.parentUser, UUID.randomUUID()))
                        .getCode())
                .isEqualTo("MISSION_NOT_FOUND");
        assertThrows(NotSameFamilyException.class, () -> service.get(other.parentUser, missionId));
    }

    private static ProfileSummary consentWithdrawn(ProfileSummary s) {
        return new ProfileSummary(
                s.profileId(),
                s.familyId(),
                s.name(),
                s.role(),
                s.ageGroup(),
                s.hasAccount(),
                s.inviteStatus(),
                s.supportMode(),
                false,
                true,
                false);
    }

    private static List<UUID> missionIds(MissionListView view) {
        return view.missions().stream().map(MissionView::missionId).toList();
    }
}
