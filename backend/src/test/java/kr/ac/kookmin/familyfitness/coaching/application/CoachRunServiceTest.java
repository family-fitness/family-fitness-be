package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunAlreadyDecidedException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunFailureCode;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunInProgressException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidRunDateException;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.NoMeasuredMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType;
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Runs;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CoachRunServiceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Family family = new Family();
    private final Family otherFamily = new Family();
    private final FakeIdentity identity = new FakeIdentity(family, otherFamily);
    private final FakeFitness fitness = new FakeFitness();
    private final InMemoryCoachRunRepository runs = new InMemoryCoachRunRepository();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository();
    private final InMemoryExerciseVideoRepository videos = new InMemoryExerciseVideoRepository(Videos.seed());
    private final List<Object> events = new ArrayList<>();
    private final CoachRunTimeLimit timeLimit = new CoachRunTimeLimit(1500, 40);
    private final CoachRunService service = serviceAt(Fixed.time());

    private CoachRunService serviceAt(AppTime time) {
        return new CoachRunService(
                runs, missions, videos, identity, identity, fitness, events::add, time, timeLimit, JSON);
    }

    private StartCoachRunCommand today(UUID profileId) {
        return on(profileId, Fixed.TODAY);
    }

    private StartCoachRunCommand on(UUID profileId, LocalDate date) {
        return new StartCoachRunCommand(profileId, date, Runs.conditions(false));
    }

    private CoachRunAcceptedView start(StartCoachRunCommand command) {
        return service.start(family.parentUser, family.familyId, command);
    }

    @BeforeEach
    void measured() {
        fitness.measured(family.child.profileId(), new FakeFitness.Item("012", 8.0));
    }

    @Test
    @DisplayName("시작하면 대상 · 날짜 · 조건이 든 RUNNING 이 저장되고, weekStart 는 그 주 월요일 · 주 1회 · 회당 minutes 분이다")
    void 시작하면_대상_날짜_조건이_든_RUNNING_이_저장된다() {
        CoachRunConditions conditions =
                new CoachRunConditions(30, false, CoachPlace.OUTDOOR, FitnessFactor.AGILITY, true);

        CoachRunAcceptedView accepted =
                start(new StartCoachRunCommand(family.child.profileId(), Fixed.TODAY, conditions));

        assertThat(accepted.status()).isEqualTo(CoachRunStatus.RUNNING);
        assertThat(accepted.pollAfterMs()).isEqualTo(1500);
        CoachRun run = runs.findById(accepted.coachRunId());
        assertThat(run.getSubjectProfileId()).isEqualTo(family.child.profileId());
        assertThat(run.getRunDate()).isEqualTo(Fixed.TODAY);
        assertThat(run.getConditions()).isEqualTo(conditions);
        assertThat(run.getWeekStart()).isEqualTo(Fixed.WEEK_START);
        assertThat(run.getDaysPerWeek()).isEqualTo(1);
        assertThat(run.getMinutesPerSession()).isEqualTo(30);
        assertThat(run.getTriggerType()).isEqualTo(TriggerType.MANUAL);
        assertThat(run.getRequestedBy()).isEqualTo(family.parent.profileId());
        assertThat(run.lockKey()).isEqualTo(family.child.profileId() + "|2026-09-09");
        assertThat(events).containsExactly(new CoachRunRequested(run.getId()));
    }

    @Test
    @DisplayName("자녀 계정은 편성을 시작할 수 없다 — 403 NOT_A_PARENT 이고 실행도 이벤트도 생기지 않는다")
    void 자녀_계정은_편성을_시작할_수_없다() {
        NotAParentException e = assertThrows(
                NotAParentException.class,
                () -> service.start(family.childUser, family.familyId, today(family.child.profileId())));

        assertThat(e.getCode()).isEqualTo("NOT_A_PARENT");
        assertThat(runs.runs).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("오늘(KST) 이전 날짜는 422 INVALID_DATE, 오늘 · 앞날은 받는다")
    void 지난_날짜는_INVALID_DATE() {
        InvalidRunDateException e = assertThrows(
                InvalidRunDateException.class, () -> start(on(family.child.profileId(), Fixed.TODAY.minusDays(1))));

        assertThat(e.getCode()).isEqualTo("INVALID_DATE");
        assertThat(runs.runs).isEmpty();
        assertThat(start(on(family.child.profileId(), Fixed.TODAY.plusDays(3))).status())
                .isEqualTo(CoachRunStatus.RUNNING);
    }

    @Test
    @DisplayName("대상이 이 가족 구성원이 아니면(다른 가족 · 없는 프로필) 422 NOT_FAMILY_MEMBER")
    void 대상이_이_가족_구성원이_아니면_NOT_FAMILY_MEMBER() {
        assertThat(assertThrows(NotFamilyMemberException.class, () -> start(today(otherFamily.child.profileId())))
                        .getCode())
                .isEqualTo("NOT_FAMILY_MEMBER");
        assertThrows(NotFamilyMemberException.class, () -> start(today(UUID.randomUUID())));
        assertThat(runs.runs).isEmpty();
    }

    @Test
    @DisplayName("대상 아이의 보호자 동의가 없으면 422 CONSENT_REQUIRED 이고 실행이 생기지 않는다")
    void 대상의_보호자_동의가_없으면_CONSENT_REQUIRED() {
        family.withdrawConsent(family.child.profileId());

        assertThat(assertThrows(ParticipantConsentRequiredException.class, () -> start(today(family.child.profileId())))
                        .getCode())
                .isEqualTo("CONSENT_REQUIRED");
        assertThat(runs.runs).isEmpty();
    }

    @Test
    @DisplayName("대상이 안 쟀으면 가족 중 다른 사람이 쟀어도 422 NO_MEASURED_MEMBER")
    void 대상이_안_쟀으면_NO_MEASURED_MEMBER() {
        fitness.latest.clear();
        fitness.measured(family.parent.profileId(), new FakeFitness.Item("028", 40.0));

        assertThat(assertThrows(NoMeasuredMemberException.class, () -> start(today(family.child.profileId())))
                        .getCode())
                .isEqualTo("NO_MEASURED_MEMBER");
        assertThat(runs.runs).isEmpty();
    }

    @Test
    @DisplayName("만 4세 미만(측정 대상 아님)은 측정 없이도 편성을 시작한다")
    void 만_4세_미만은_측정_없이도_시작한다() {
        ProfileDetails toddler = family.addChild("막내", Fixed.TODAY.minusYears(3));

        assertThat(start(today(toddler.profileId())).status()).isEqualTo(CoachRunStatus.RUNNING);
    }

    @Test
    @DisplayName("같은 (프로필, 날짜)에 RUNNING 이 있으면 409 RUN_IN_PROGRESS — 다른 날 · 형제는 따로 짠다")
    void 같은_프로필_날짜에_RUNNING_이_있으면_RUN_IN_PROGRESS() {
        ProfileDetails sibling = family.addChild("하늘", LocalDate.of(2017, 4, 2));
        fitness.measured(sibling.profileId(), new FakeFitness.Item("012", 6.0));
        start(today(family.child.profileId()));

        CoachRunInProgressException e =
                assertThrows(CoachRunInProgressException.class, () -> start(today(family.child.profileId())));

        assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
        assertThat(start(on(family.child.profileId(), Fixed.TODAY.plusDays(1))).status())
                .isEqualTo(CoachRunStatus.RUNNING);
        assertThat(start(today(sibling.profileId())).status()).isEqualTo(CoachRunStatus.RUNNING);
        assertThat(runs.runs).hasSize(3);
    }

    @Test
    @DisplayName("동시에 들어온 요청이 잠금을 먼저 잡았으면(유니크 인덱스 위반) 409 RUN_IN_PROGRESS 이고 이벤트도 없다")
    void 동시_요청이_잠금을_먼저_잡았으면_RUN_IN_PROGRESS() {
        InMemoryCoachRunRepository racing = new InMemoryCoachRunRepository() {
            @Override
            public boolean isLocked(String lockKey) {
                return false; // 존재 검사 뒤 · INSERT 전에 다른 요청이 끼어든 상황
            }

            @Override
            public boolean insertRunning(CoachRun run) {
                return false;
            }
        };
        CoachRunService racingService = new CoachRunService(
                racing, missions, videos, identity, identity, fitness, events::add, Fixed.time(), timeLimit, JSON);

        assertThrows(
                CoachRunInProgressException.class,
                () -> racingService.start(family.parentUser, family.familyId, today(family.child.profileId())));
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("만든 지 223초가 안 된 RUNNING 은 막고, 223초를 넘긴 RUNNING 은 FAILED 로 바꾸고 잠금을 풀어 새 편성을 받는다")
    void 오래된_RUNNING_은_FAILED_로_바꾸고_잠금을_푼다() {
        CoachRun old = runs.save(Runs.running(
                family.familyId,
                family.child.profileId(),
                Fixed.TODAY,
                family.parent.profileId(),
                Fixed.NOW.minusSeconds(222)));

        assertThrows(CoachRunInProgressException.class, () -> start(today(family.child.profileId())));

        CoachRunAcceptedView accepted = serviceAt(Fixed.time(Fixed.NOW.plusSeconds(2)))
                .start(family.parentUser, family.familyId, today(family.child.profileId()));
        assertThat(accepted.status()).isEqualTo(CoachRunStatus.RUNNING);
        assertThat(runs.currentStatus(old.getId())).isEqualTo(CoachRunStatus.FAILED);
        assertThat(runs.findById(old.getId()).getFailureReason()).startsWith("stale: 223초");
        assertThat(service.get(family.parentUser, old.getId()).failureCode()).isEqualTo(CoachRunFailureCode.STALE);
    }

    @Test
    @DisplayName("조회는 FAILED 면 까닭 코드를 싣고 개발자용 원문은 싣지 않는다 — FAILED 가 아니면 null, 알림은 빈 목록")
    void 조회는_FAILED_면_까닭_코드를_싣는다() {
        CoachRun refused = runs.save(Runs.running(
                family.familyId, family.child.profileId(), Fixed.TODAY, family.parent.profileId(), Fixed.NOW));
        refused.fail(
                CoachRunFailureCode.NO_CITATIONS,
                "refused: no_relevant_source",
                Fixed.NOW,
                List.of(),
                true,
                "no_relevant_source");
        CoachRun waiting = runs.save(awaitingOfWeek());

        CoachRunView failedView = service.get(family.parentUser, refused.getId());
        CoachRunView waitingView = service.get(family.parentUser, waiting.getId());

        assertThat(failedView.status()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(failedView.failureCode()).isEqualTo(CoachRunFailureCode.NO_CITATIONS);
        assertThat(failedView.notices()).isEmpty();
        assertThat(failedView.toString()).doesNotContain("no_relevant_source");
        assertThat(waitingView.failureCode()).isNull();
        assertThat(waitingView.notices()).isEmpty();
    }

    @Test
    @DisplayName("조회는 제안 원문(proposal_json)의 AI 알림(notices)을 그대로 싣는다 — 원문을 읽지 못하면 빈 목록")
    void 조회는_제안_원문의_AI_알림을_싣는다() {
        CoachRun withNotices = Runs.running(
                family.familyId, family.child.profileId(), Fixed.TODAY, family.parent.profileId(), Fixed.NOW);
        withNotices.complete(
                List.of(),
                proposals(),
                "{\"missions\":[],\"citations\":[],\"notices\":[\"또래 영상이 모자라 다른 연령대 영상도 골랐습니다\"]}",
                "요약",
                null,
                Fixed.NOW);
        runs.save(withNotices);
        CoachRun broken = Runs.running(
                family.familyId,
                family.child.profileId(),
                Fixed.TODAY.plusDays(1),
                family.parent.profileId(),
                Fixed.NOW);
        broken.complete(List.of(), proposals(), "{not json", "요약", null, Fixed.NOW);
        runs.save(broken);

        assertThat(service.get(family.parentUser, withNotices.getId()).notices())
                .containsExactly("또래 영상이 모자라 다른 연령대 영상도 골랐습니다");
        assertThat(service.get(family.parentUser, broken.getId()).notices()).isEmpty();
    }

    @Test
    @DisplayName("새 편성은 같은 (프로필, 날짜)의 승인 대기 제안만 고정 사유로 거절한다 — 승인된 것 · 다른 날 것은 그대로다")
    void 새_편성은_같은_프로필_날짜의_승인_대기_제안만_거절한다() {
        UUID child = family.child.profileId();
        UUID parent = family.parent.profileId();
        CoachRun waiting = runs.save(
                Runs.awaiting(family.familyId, child, Fixed.TODAY, parent, Fixed.NOW.minusSeconds(600), proposals()));
        CoachRun tomorrow = runs.save(Runs.awaiting(
                family.familyId, child, Fixed.TODAY.plusDays(1), parent, Fixed.NOW.minusSeconds(500), proposals()));
        CoachRun approved = runs.save(
                Runs.awaiting(family.familyId, child, Fixed.TODAY, parent, Fixed.NOW.minusSeconds(400), proposals()));
        service.approve(family.parentUser, approved.getId());

        CoachRunAcceptedView accepted = start(today(child));

        assertThat(accepted.status()).isEqualTo(CoachRunStatus.RUNNING);
        assertThat(runs.currentStatus(waiting.getId())).isEqualTo(CoachRunStatus.REJECTED);
        assertThat(runs.findById(waiting.getId()).getRejectedReason()).isEqualTo("새 제안으로 바뀌었어요");
        assertThat(runs.currentStatus(tomorrow.getId())).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(runs.currentStatus(approved.getId())).isEqualTo(CoachRunStatus.APPROVED);
    }

    @Test
    @DisplayName("latest 는 profileId 가 있으면 그 아이의 가장 최근 실행, 없으면 가족의 가장 최근 실행이다")
    void latest_는_profileId_로_아이마다_찾는다() {
        ProfileDetails sibling = family.addChild("하늘", LocalDate.of(2017, 4, 2));
        UUID parent = family.parent.profileId();
        CoachRun childRun = runs.save(Runs.awaiting(
                family.familyId,
                family.child.profileId(),
                Fixed.TODAY,
                parent,
                Fixed.NOW.minusSeconds(60),
                proposals()));
        CoachRun siblingRun = runs.save(Runs.awaiting(
                family.familyId, sibling.profileId(), Fixed.TODAY, parent, Fixed.NOW.minusSeconds(30), List.of()));

        CoachRunView ofChild = service.latest(family.parentUser, family.familyId, family.child.profileId());
        CoachRunView ofFamily = service.latest(family.childUser, family.familyId, null);

        assertThat(ofChild.coachRunId()).isEqualTo(childRun.getId());
        assertThat(ofChild.profileId()).isEqualTo(family.child.profileId());
        assertThat(ofChild.date()).isEqualTo(Fixed.TODAY);
        assertThat(ofChild.canApprove()).isTrue();
        assertThat(ofFamily.coachRunId()).isEqualTo(siblingRun.getId());
        assertThat(ofFamily.canApprove()).isFalse();
    }

    @Test
    @DisplayName("latest 는 실행이 없으면 404 COACH_RUN_NOT_FOUND, 다른 가족 계정이면 403")
    void latest_는_없으면_404_다른_가족이면_403() {
        assertThat(assertThrows(
                                CoachRunNotFoundException.class,
                                () -> service.latest(family.parentUser, family.familyId, family.child.profileId()))
                        .getCode())
                .isEqualTo("COACH_RUN_NOT_FOUND");
        assertThrows(CoachRunNotFoundException.class, () -> service.latest(family.parentUser, family.familyId, null));
        assertThrows(NotSameFamilyException.class, () -> service.latest(family.outsiderUser, family.familyId, null));
    }

    @Test
    @DisplayName("미션으로 옮길 참여자 중 보호자 동의를 거둔 아이가 있으면 승인은 422 CONSENT_REQUIRED 이고 아무것도 바뀌지 않는다")
    void 참여자_중_보호자_동의를_거둔_아이가_있으면_승인은_CONSENT_REQUIRED() {
        CoachRun run = runs.save(awaitingOfWeek());
        family.withdrawConsent(family.child.profileId());

        ParticipantConsentRequiredException e = assertThrows(
                ParticipantConsentRequiredException.class, () -> service.approve(family.parentUser, run.getId()));

        assertThat(e.getCode()).isEqualTo("CONSENT_REQUIRED");
        assertThat(runs.currentStatus(run.getId())).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(missions.missions).isEmpty();
    }

    @Test
    @DisplayName("다른 가족 계정은 시작·조회할 수 없다")
    void 다른_가족_계정은_시작_조회할_수_없다() {
        assertThrows(
                NotSameFamilyException.class,
                () -> service.start(family.outsiderUser, family.familyId, today(family.child.profileId())));
        CoachRun run = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals()));
        assertThrows(NotSameFamilyException.class, () -> service.get(family.outsiderUser, run.getId()));
        assertThrows(CoachRunNotFoundException.class, () -> service.get(family.parentUser, UUID.randomUUID()));
    }

    @Test
    @DisplayName("조회는 부모에게만 canApprove 를 주고 제안에 영상 제목·배지를 붙인다 — 옛 주간 실행은 profileId · date 가 null")
    void 조회는_부모에게만_canApprove_를_주고_제안에_영상_제목_배지를_붙인다() {
        CoachRun run = runs.save(awaitingOfWeek());

        CoachRunView parentView = service.get(family.parentUser, run.getId());
        CoachRunView childView = service.get(family.childUser, run.getId());

        assertThat(parentView.canApprove()).isTrue();
        assertThat(childView.canApprove()).isFalse();
        assertThat(parentView.status()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(parentView.profileId()).isNull();
        assertThat(parentView.date()).isNull();
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
    @DisplayName("제안 칸은 조회에 미션 칸과 같은 모양으로 실리고, 승인하면 차례 그대로 미션 칸으로 복사된다")
    void 제안_칸은_조회에_실리고_승인하면_차례_그대로_미션_칸으로_복사된다() {
        List<MissionSession> sessions = List.of(
                new MissionSession(
                        1,
                        SessionPhase.WARMUP,
                        "넙다리 안쪽 늘리기 (나비자세)",
                        FitnessFactor.FLEXIBILITY,
                        1,
                        new SessionClip("Eg3GpTv7z8s", 144, 182, "넙다리 안쪽 늘리기 (나비자세)")),
                new MissionSession(2, SessionPhase.MAIN, "가슴펴기", FitnessFactor.FLEXIBILITY, 18, null),
                new MissionSession(
                        3,
                        SessionPhase.COOLDOWN,
                        "다리 뒤 늘리기",
                        null,
                        1,
                        new SessionClip("Eg3GpTv7z8s", 1426, 1466, null)));
        CoachRun run = runs.save(Runs.awaiting(
                family.familyId,
                family.child.profileId(),
                Fixed.TODAY,
                family.parent.profileId(),
                Fixed.NOW,
                List.of(new CoachProposalItem(
                        0,
                        "유연성 키우기 20분",
                        "TIMER_MINUTES",
                        20,
                        "이유",
                        null,
                        Fixed.TODAY,
                        Fixed.TODAY,
                        List.of(new ProposalParticipant(family.child.profileId(), ProfileRole.CHILD, "주행자")),
                        null,
                        List.of(),
                        null,
                        null,
                        sessions))));

        assertThat(service.get(family.parentUser, run.getId())
                        .proposals()
                        .getFirst()
                        .sessions())
                .containsExactly(
                        new MissionSessionView(
                                1,
                                SessionPhase.WARMUP,
                                "넙다리 안쪽 늘리기 (나비자세)",
                                FitnessFactor.FLEXIBILITY,
                                1,
                                new SessionClipView("Eg3GpTv7z8s", 144, 182, "넙다리 안쪽 늘리기 (나비자세)")),
                        new MissionSessionView(2, SessionPhase.MAIN, "가슴펴기", FitnessFactor.FLEXIBILITY, 18, null),
                        new MissionSessionView(
                                3,
                                SessionPhase.COOLDOWN,
                                "다리 뒤 늘리기",
                                null,
                                1,
                                new SessionClipView("Eg3GpTv7z8s", 1426, 1466, null)));

        ApproveCoachRunView approved = service.approve(family.parentUser, run.getId());

        Mission mission =
                missions.findById(approved.createdMissions().getFirst().missionId());
        assertThat(mission.getSessions()).isEqualTo(sessions);
        assertThat(mission.getTargetValue()).isEqualTo(20);
    }

    @Test
    @DisplayName("칸 없는 제안(옛 실행)은 조회에 빈 칸 목록으로 실리고 칸 없는 미션이 된다")
    void 칸_없는_제안은_빈_칸_목록이고_칸_없는_미션이_된다() {
        CoachRun run = runs.save(awaitingOfWeek());

        assertThat(service.get(family.parentUser, run.getId())
                        .proposals()
                        .getFirst()
                        .sessions())
                .isEmpty();
        ApproveCoachRunView approved = service.approve(family.parentUser, run.getId());
        assertThat(missions.findById(approved.createdMissions().getFirst().missionId())
                        .getSessions())
                .isEmpty();
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
                Instant.EPOCH);
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
}
