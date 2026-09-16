package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.AlreadyRunThisWeekException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachApprover;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunAlreadyDecidedException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunInProgressException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.NoMeasuredMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType;
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoachRunServiceTest {
    private final Family family = new Family();
    private final FakeIdentity identity = new FakeIdentity(family);
    private final FakeFitness fitness = new FakeFitness();
    private final InMemoryCoachRunRepository runs = new InMemoryCoachRunRepository();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository();
    private final InMemoryExerciseVideoRepository videos = new InMemoryExerciseVideoRepository(Videos.seed());
    private final List<Object> events = new ArrayList<>();
    private final CoachRunService service =
            new CoachRunService(runs, missions, videos, identity, identity, fitness, events::add, Fixed.time());
    private final StartCoachRunCommand command = new StartCoachRunCommand(null, 3, 15);

    @BeforeEach
    void measured() {
        fitness.measured(family.child.profileId(), new FakeFitness.Item("012", 8.0));
    }

    @Test
    @DisplayName("시작하면 이번 주 월요일의 RUNNING 실행이 저장되고 이벤트가 커밋 후 처리를 위해 발행된다")
    void 시작하면_이번_주_월요일의_RUNNING_실행이_저장되고_이벤트가_커밋_후_처리를_위해_발행된다() {
        CoachRunAcceptedView accepted = service.start(family.childUser, family.familyId, command);

        assertThat(accepted.status()).isEqualTo(CoachRunStatus.RUNNING);
        assertThat(accepted.pollAfterMs()).isEqualTo(1500);
        CoachRun run = runs.findById(accepted.coachRunId());
        assertThat(run.getWeekStart()).isEqualTo(Fixed.WEEK_START);
        assertThat(run.getTriggerType()).isEqualTo(TriggerType.MANUAL);
        assertThat(run.getRequestedBy()).isEqualTo(family.child.profileId());
        assertThat(run.getDaysPerWeek()).isEqualTo(3);
        assertThat(events).containsExactly(new CoachRunRequested(run.getId()));
    }

    @Test
    @DisplayName("스케줄 실행은 SCHEDULE 트리거 · 기본 3회 15분 · 요청자 없이 시작하고, 이미 실행 중이거나 측정이 없으면 조용히 건너뛴다")
    void 스케줄_실행은_SCHEDULE_트리거_기본_3회_15분_요청자_없이_시작하고_이미_실행_중이거나_측정이_없으면_조용히_건너뛴다() {
        UUID runId = service.startScheduled(family.familyId);

        CoachRun run = runs.findById(runId);
        assertThat(run.getTriggerType()).isEqualTo(TriggerType.SCHEDULE);
        assertThat(run.getRequestedBy()).isNull();
        assertThat(run.getDaysPerWeek()).isEqualTo(3);
        assertThat(run.getMinutesPerSession()).isEqualTo(15);
        assertThat(events).containsExactly(new CoachRunRequested(run.getId()));

        assertThat(service.startScheduled(family.familyId)).isNull();

        Family other = new Family();
        identity.families.add(other);
        assertThat(service.startScheduled(other.familyId)).isNull();
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("weekStart 는 아무 요일이나 받아 그 주 월요일로 맞춘다")
    void weekStart_는_아무_요일이나_받아_그_주_월요일로_맞춘다() {
        CoachRunAcceptedView accepted = service.start(
                family.parentUser, family.familyId, new StartCoachRunCommand(LocalDate.of(2026, 9, 17), 3, 15));

        assertThat(runs.findById(accepted.coachRunId()).getWeekStart()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("같은 가족에 RUNNING 실행이 있으면 RUN_IN_PROGRESS")
    void 같은_가족에_RUNNING_실행이_있으면_RUN_IN_PROGRESS() {
        service.start(family.parentUser, family.familyId, command);

        CoachRunInProgressException e = assertThrows(
                CoachRunInProgressException.class,
                () -> service.start(
                        family.parentUser,
                        family.familyId,
                        new StartCoachRunCommand(LocalDate.of(2026, 9, 21), 3, 15)));
        assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
    }

    @Test
    @DisplayName("같은 주에 승인 대기·승인된 실행이 있으면 ALREADY_RUN_THIS_WEEK, 거절·실패면 다시 시작할 수 있다")
    void 같은_주에_승인_대기_승인된_실행이_있으면_ALREADY_RUN_THIS_WEEK_거절_실패면_다시_시작할_수_있다() {
        CoachRun awaiting = awaitingOfWeek();
        runs.save(awaiting);
        assertThat(assertThrows(
                                AlreadyRunThisWeekException.class,
                                () -> service.start(family.parentUser, family.familyId, command))
                        .getCode())
                .isEqualTo("ALREADY_RUN_THIS_WEEK");

        awaiting.reject(parentApprover(), "다음에", Fixed.NOW);
        runs.save(awaiting);
        assertThat(service.start(family.parentUser, family.familyId, command).status())
                .isEqualTo(CoachRunStatus.RUNNING);
    }

    @Test
    @DisplayName("측정 기록이 있는 구성원이 없으면 NO_MEASURED_MEMBER")
    void 측정_기록이_있는_구성원이_없으면_NO_MEASURED_MEMBER() {
        fitness.latest.clear();

        assertThat(assertThrows(
                                NoMeasuredMemberException.class,
                                () -> service.start(family.parentUser, family.familyId, command))
                        .getCode())
                .isEqualTo("NO_MEASURED_MEMBER");
        assertThat(runs.runs).isEmpty();
    }

    @Test
    @DisplayName("다른 가족 계정은 시작·조회할 수 없다")
    void 다른_가족_계정은_시작_조회할_수_없다() {
        assertThrows(NotSameFamilyException.class, () -> service.start(family.outsiderUser, family.familyId, command));
        CoachRun run = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals()));
        assertThrows(NotSameFamilyException.class, () -> service.get(family.outsiderUser, run.getId()));
        assertThrows(CoachRunNotFoundException.class, () -> service.get(family.parentUser, UUID.randomUUID()));
    }

    @Test
    @DisplayName("조회는 부모에게만 canApprove 를 주고 제안에 영상 제목·배지를 붙인다")
    void 조회는_부모에게만_canApprove_를_주고_제안에_영상_제목_배지를_붙인다() {
        CoachRun run = runs.save(awaitingOfWeek());

        CoachRunView parentView = service.get(family.parentUser, run.getId());
        CoachRunView childView = service.get(family.childUser, run.getId());

        assertThat(parentView.canApprove()).isTrue();
        assertThat(childView.canApprove()).isFalse();
        assertThat(parentView.status()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(parentView.missionCount()).isEqualTo(0);
        assertThat(parentView.proposals()).hasSize(1);
        ProposalView proposal = parentView.proposals().getFirst();
        assertThat(proposal.video().title()).isEqualTo("영상 IdpXx2gm90o");
        assertThat(proposal.video().url()).isEqualTo("https://www.youtube.com/watch?v=IdpXx2gm90o");
        assertThat(proposal.video().badges()).containsExactly("조용함", "좁은 공간 OK", "준비물 없음");
        assertThat(proposal.startDate()).isEqualTo(Fixed.WEEK_START);
        assertThat(proposal.endDate()).isEqualTo(Fixed.WEEK_START.plusDays(6));
        assertThat(proposal.participants())
                .singleElement()
                .extracting(ProposalParticipantView::coachRole)
                .isEqualTo("주행자");
    }

    @Test
    @DisplayName("부모가 승인하면 한 번만 미션이 만들어지고 승인자는 부모 프로필이다")
    void 부모가_승인하면_한_번만_미션이_만들어지고_승인자는_부모_프로필이다() {
        CoachRun run = runs.save(awaitingOfWeek());

        ApproveCoachRunView view = service.approve(family.parentUser, run.getId());

        assertThat(view.status()).isEqualTo(CoachRunStatus.APPROVED);
        assertThat(view.approvedBy()).isEqualTo(family.parent.profileId());
        assertThat(view.approvedAt()).isEqualTo(Fixed.NOW);
        assertThat(view.createdMissions()).hasSize(1);
        assertThat(view.createdMissions().getFirst().origin()).isEqualTo(MissionOrigin.COACH);
        Mission mission = missions.findById(view.createdMissions().getFirst().missionId());
        assertThat(mission.getCoachRunId()).isEqualTo(run.getId());
        assertThat(mission.getParticipants().stream()
                        .map(MissionParticipant::getProfileId)
                        .toList())
                .containsExactly(family.child.profileId());
        assertThat(service.get(family.parentUser, run.getId()).missionCount()).isEqualTo(1);
        assertThat(service.get(family.parentUser, run.getId()).canApprove()).isFalse();

        CoachRunAlreadyDecidedException again = assertThrows(
                CoachRunAlreadyDecidedException.class, () -> service.approve(family.parentUser, run.getId()));
        assertThat(again.getCode()).isEqualTo("ALREADY_APPROVED");
        assertThat(missions.missions).hasSize(1);
    }

    @Test
    @DisplayName("자녀·다른 가족·거절된 실행은 승인할 수 없다")
    void 자녀_다른_가족_거절된_실행은_승인할_수_없다() {
        CoachRun run = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals()));

        assertThrows(NotAParentException.class, () -> service.approve(family.childUser, run.getId()));
        assertThrows(NotSameFamilyException.class, () -> service.approve(family.outsiderUser, run.getId()));
        assertThat(missions.missions).isEmpty();

        RejectCoachRunView rejected = service.reject(family.parentUser, run.getId(), "이번 주는 쉬어요");
        assertThat(rejected.status()).isEqualTo(CoachRunStatus.REJECTED);
        assertThat(rejected.rejectedReason()).isEqualTo("이번 주는 쉬어요");
        assertThat(rejected.missionCount()).isEqualTo(0);

        assertThat(assertThrows(
                                CoachRunAlreadyDecidedException.class,
                                () -> service.approve(family.parentUser, run.getId()))
                        .getCode())
                .isEqualTo("INVALID_STATE");
        assertThat(assertThrows(
                                CoachRunAlreadyDecidedException.class,
                                () -> service.reject(family.parentUser, run.getId(), null))
                        .getCode())
                .isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("참여자 없는 제안 항목은 미션으로 만들지 않는다")
    void 참여자_없는_제안_항목은_미션으로_만들지_않는다() {
        CoachRun run = runs.save(CoachRun.awaitingApproval(
                UUID.randomUUID(), family.familyId, List.of(new CoachProposalItem(0, "빈 항목", "TIMER_MINUTES", 30))));

        assertThat(service.approve(family.parentUser, run.getId()).createdMissions())
                .isEmpty();
    }

    private CoachRun awaitingOfWeek() {
        return CoachRun.awaitingApproval(
                UUID.randomUUID(),
                family.familyId,
                proposals(),
                Fixed.WEEK_START,
                List.of(),
                null,
                3,
                15,
                java.time.Instant.EPOCH);
    }

    private List<CoachProposalItem> proposals() {
        return List.of(new CoachProposalItem(
                0,
                "같이 늘이는 한 주",
                "TIMER_MINUTES",
                45,
                "부모 문구",
                null,
                null,
                null,
                List.of(new ProposalParticipant(family.child.profileId(), ProfileRole.CHILD, "주행자")),
                new ProposalVideo("IdpXx2gm90o", 96),
                List.of(),
                null,
                null));
    }

    private CoachApprover parentApprover() {
        return new CoachApprover(family.parent.profileId(), family.familyId, true);
    }
}
