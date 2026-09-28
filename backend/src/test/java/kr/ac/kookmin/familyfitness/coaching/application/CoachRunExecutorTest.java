package kr.ac.kookmin.familyfitness.coaching.application;

import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.COOLDOWN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.MAIN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.WARMUP;
import static kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseClipRepository.clip;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.support.FakeAiGateway;
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.AiBadRequestException;
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunAccepted;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CoachRunExecutorTest {
    private final Family family = new Family();
    private final FakeIdentity identity = new FakeIdentity(family);
    private final FakeFitness fitness = newFitness();
    private final InMemoryCoachRunRepository runs = new InMemoryCoachRunRepository();
    private final FakeAiGateway gateway = new FakeAiGateway();
    private final InMemoryExerciseVideoRepository videos = new InMemoryExerciseVideoRepository(Videos.seed());
    /** 기본은 비어 있다 — 대체 편성은 영상 한 편 통째로 짠다. 클립으로 짜는 시험만 채운다. */
    private final InMemoryExerciseClipRepository clips = new InMemoryExerciseClipRepository();

    private final CoachRunPipeline pipeline = new CoachRunPipeline(
            runs,
            identity,
            fitness,
            JsonMapper.builder().build(),
            Fixed.time(),
            new LabelBasedProposalPlanner(fitness, videos, clips),
            clips,
            videos);
    private final CoachRunExecutor executor = new CoachRunExecutor(pipeline, gateway, 0, 3);

    private FakeFitness newFitness() {
        FakeFitness fakeFitness = new FakeFitness();
        fakeFitness.measured(
                family.child.profileId(), new FakeFitness.Item("012", 8.0), new FakeFitness.Item("028", 45.0));
        return fakeFitness;
    }

    private CoachRun runningRun() {
        return runningRun(family.child.profileId(), true, null);
    }

    private CoachRun runningRun(UUID subject, boolean withParent, @Nullable FitnessFactor focus) {
        return runs.save(CoachRun.start(
                UUID.randomUUID(),
                family.familyId,
                subject,
                Fixed.TODAY,
                new CoachRunConditions(20, true, CoachPlace.HOME, focus, withParent),
                family.parent.profileId(),
                Fixed.NOW));
    }

    private static List<UUID> participantIds(CoachProposalItem item) {
        return item.participants().stream().map(ProposalParticipant::profileId).toList();
    }

    @Test
    @DisplayName("AI 에는 대상 아이 한 명(주행자)의 그날만 이름 없이 보내고, 조건은 하루 1회 · minutes · 집이면 좁은 곳 · 도구 없음이다")
    void AI_에는_대상_아이_한_명의_그날만_보낸다() {
        CoachRun run = runningRun();

        executor.execute(run.getId());

        assertThat(gateway.startRequests).hasSize(1);
        CoachRunRequest request = gateway.startRequests.getFirst();
        assertThat(request.startDate()).isEqualTo("2026-09-09");
        assertThat(request.weeks()).isEqualTo(1);
        assertThat(request.profiles()).singleElement().satisfies(it -> {
            assertThat(it.role()).isEqualTo("주행자");
            assertThat(it.profile().profileRef()).isEqualTo(ProfileRef.of(family.child.profileId()));
            assertThat(it.profile().age()).isEqualTo(11);
            assertThat(it.profile().measurements()).containsKeys("012", "028");
            assertThat(it.profile().inputLevel()).isEqualTo("L2");
        });
        assertThat(request.constraints())
                .isEqualTo(new CoachRunRequest.Constraints(1, 20, null, true, true, true, null, true));
        assertThat(request.toString()).doesNotContain("민준");
    }

    @Test
    @DisplayName("succeeded 면 하루짜리 제안이 되고, 참여자는 대상 아이 + withParent 면 요청한 보호자(동반자)다")
    void succeeded_면_하루짜리_제안이_되고_참여자는_대상과_요청한_보호자다() {
        CoachRun run = runningRun();

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getAiRunId()).startsWith("cr_");
        assertThat(saved.lockKey()).isNull();
        assertThat(saved.getSteps().stream().map(CoachStep::name).toList())
                .containsExactly("assess", "retrieve", "compose", "verify");
        assertThat(saved.getSummary()).contains("유연성");
        assertThat(saved.getProposalJson()).contains("유연성 키우기 20분");
        CoachProposalItem item = saved.getProposals().getFirst();
        assertThat(saved.getProposals()).hasSize(1);
        assertThat(item.targetMetric()).isEqualTo("TIMER_MINUTES");
        assertThat(item.targetValue()).isEqualTo(20);
        assertThat(item.startsOn()).isEqualTo(Fixed.TODAY);
        assertThat(item.endsOn()).isEqualTo(Fixed.TODAY);
        assertThat(item.video()).isEqualTo(new ProposalVideo("Eg3GpTv7z8s", 144));
        assertThat(item.participants())
                .containsExactly(
                        new ProposalParticipant(family.child.profileId(), ProfileRole.CHILD, "주행자"),
                        new ProposalParticipant(family.parent.profileId(), ProfileRole.PARENT, "동반자"));
        assertThat(item.citations().stream().map(ProposalCitation::index).toList())
                .containsExactly(1, 2);
        // 스텁은 AI 가짓수 규칙대로 20분 = 준비 2 · 본 4 · 정리 1, 칸 분은 준비 · 정리 1분에 남는 17분을 본운동이 나눈다
        assertThat(item.sessions().stream().map(MissionSession::phase).toList())
                .containsExactly(WARMUP, WARMUP, MAIN, MAIN, MAIN, MAIN, COOLDOWN);
        assertThat(item.sessions().stream().map(MissionSession::minutes).toList())
                .containsExactly(1, 1, 5, 4, 4, 4, 1);
        assertThat(item.sessions().getFirst().clip()).isEqualTo(new SessionClip("Eg3GpTv7z8s", 144, 182, null));
    }

    @Test
    @DisplayName("AI 장애 때 클립 표가 있으면 대상 연령대 · 조건에 맞는 클립을 가짓수 규칙대로 골라 칸으로 짠다 — 요인 같은 것, 한 세트 60초에 가까운 것 먼저")
    void AI_장애_때_클립_표가_있으면_가짓수_규칙대로_클립을_골라_칸으로_짠다() {
        fitness.measured(
                family.child.profileId(),
                new FactorPoint(FitnessFactor.FLEXIBILITY, "012", 24),
                new FactorPoint(FitnessFactor.CARDIO, "020", 80),
                new FakeFitness.Item("012", 8.0));
        String eg = "Eg3GpTv7z8s";
        List.of(
                        clip("IdpXx2gm90o", 56, 116, "스트레칭", WARMUP, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 144, 182, "나비자세", WARMUP, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 188, 226, "고양이자세", WARMUP, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 500, 534, "양팔 펴기", MAIN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 536, 588, "가슴펴기", MAIN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 614, 674, "팔꿈치 펴기", MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH),
                        clip(eg, 680, 754, "팔 스트레칭", MAIN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 1104, 1150, "다리 늘리기", MAIN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 1426, 1466, "다리 뒤 늘리기", COOLDOWN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 1822, 1860, "어깨 늘리기", COOLDOWN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        // 다른 연령대 · 시끄러운 클립은 60초 · 같은 요인이어도 고르지 않는다
                        clip("sample00005", 0, 60, "유아 늘이기", MAIN, FitnessFactor.FLEXIBILITY, AgeGroup.TODDLER),
                        noisy(clip(eg, 2000, 2060, "제자리 뛰기", MAIN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH)))
                .forEach(it -> clips.clips.put(it.clipId(), it));
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getSteps().get(1).summary()).endsWith("클립 라벨 기반 편성 · 클립 7개");
        assertThat(saved.getSteps().get(2).summary()).isEqualTo("하루 20분 · 준비 2 · 본 4 · 정리 1");
        CoachProposalItem item = saved.getProposals().getFirst();
        assertThat(item.sessions().stream().map(MissionSession::title).toList())
                .containsExactly("스트레칭", "나비자세", "가슴펴기", "팔 스트레칭", "다리 늘리기", "양팔 펴기", "다리 뒤 늘리기");
        assertThat(item.sessions().stream().map(MissionSession::minutes).toList())
                .containsExactly(1, 1, 5, 4, 4, 4, 1);
        assertThat(item.targetValue()).isEqualTo(20);
        assertThat(item.sessions().get(2).clip()).isEqualTo(new SessionClip(eg, 536, 588, "가슴펴기"));
        assertThat(item.video()).isEqualTo(new ProposalVideo("IdpXx2gm90o", 56));
        // 인용: 규준 1건 + 클립이 나온 영상마다 1건
        assertThat(item.citations().stream().map(ProposalCitation::chunkId).toList())
                .containsExactly("norm:유소년-012", "video:IdpXx2gm90o", "video:" + eg);
    }

    @Test
    @DisplayName("withParent 가 아니면 참여자는 대상 아이뿐이다 — 응원 부모도 다른 구성원도 들어가지 않는다")
    void withParent_가_아니면_참여자는_대상_아이뿐이다() {
        CoachRun run = runningRun(family.child.profileId(), false, null);
        gateway.onPoll = id -> resultWithCheer(id);

        executor.execute(run.getId());

        assertThat(participantIds(runs.findById(run.getId()).getProposals().getFirst()))
                .containsExactly(family.child.profileId());
    }

    @Test
    @DisplayName("부모가 고른 힘은 focus_factor 로 AI 에 실리고 스텁 제안의 요인이 된다")
    void 부모가_고른_힘은_focus_factor_로_실린다() {
        CoachRun run = runningRun(family.child.profileId(), false, FitnessFactor.AGILITY);

        executor.execute(run.getId());

        assertThat(gateway.startRequests.getFirst().constraints().focusFactor()).isEqualTo("민첩성");
        assertThat(runs.findById(run.getId()).getProposals().getFirst().title()).isEqualTo("민첩성 키우기 20분");
    }

    @Test
    @DisplayName("refused 면 FAILED 이고 사유는 refused 접두어로 남는다")
    void refused_면_FAILED_이고_사유는_refused_접두어로_남는다() {
        CoachRun run = runningRun();
        gateway.onPoll = id -> new CoachRunResult(
                id,
                "refused",
                List.of(new CoachRunResult.Step(1, "assess", "refused", "연령 필터")),
                null,
                true,
                "age_filter_empty");

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("refused: age_filter_empty");
        assertThat(saved.isAiRefused()).isTrue();
        assertThat(saved.getAiRefusalReason()).isEqualTo("age_filter_empty");
        assertThat(saved.getSteps())
                .singleElement()
                .extracting(CoachStep::status)
                .isEqualTo("refused");
        assertThat(saved.getProposals()).isEmpty();
        assertThat(saved.lockKey()).isNull();
    }

    @Test
    @DisplayName("running 이 계속되면 최대 횟수까지 폴링하고 timeout 으로 FAILED")
    void running_이_계속되면_최대_횟수까지_폴링하고_timeout_으로_FAILED() {
        CoachRun run = runningRun();
        gateway.onPoll = id -> new CoachRunResult(id, "running", List.of(), null, false, null);

        executor.execute(run.getId());

        assertThat(gateway.pollCount).isEqualTo(3);
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureReason()).startsWith("timeout");
    }

    @Test
    @DisplayName("AI 장애인데 고른 힘도 측정 약점도 없으면 FAILED 로 끝나고 상태 전이는 한 번만 일어난다")
    void AI_장애인데_근거가_없으면_FAILED_로_끝난다() {
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("AI 장애");

        executor.execute(run.getId());
        assertThat(runs.findById(run.getId()).getStatus()).isEqualTo(CoachRunStatus.FAILED);
    }

    @Test
    @DisplayName("AI 장애여도 측정 약점이 있으면 대상 아이의 그날 하루짜리로 대체 편성해 AWAITING_APPROVAL")
    void AI_장애여도_측정_약점이_있으면_하루짜리로_대체_편성한다() {
        fitness.measured(
                family.child.profileId(),
                new FactorPoint(FitnessFactor.FLEXIBILITY, "012", 24),
                new FactorPoint(FitnessFactor.CARDIO, "020", 80),
                new FakeFitness.Item("012", 8.0));
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getAiRunId()).isNull();
        assertThat(saved.getSteps().stream().map(CoachStep::status).toList())
                .containsExactly("ok", "partial", "ok", "ok");
        assertThat(saved.getSteps().get(1).summary()).contains("AI 서비스 장애");
        assertThat(saved.getProposals()).hasSize(1);
        CoachProposalItem item = saved.getProposals().getFirst();
        assertThat(item.title()).isEqualTo("유연성 키우기 20분");
        assertThat(item.targetValue()).isEqualTo(20);
        assertThat(item.startsOn()).isEqualTo(Fixed.TODAY);
        assertThat(item.endsOn()).isEqualTo(Fixed.TODAY);
        assertThat(item.video().videoId()).isEqualTo("IdpXx2gm90o");
        assertThat(item.citations().stream().map(ProposalCitation::label).toList())
                .anyMatch(it -> it.startsWith("국민체력100 규준"));
        assertThat(item.participants())
                .containsExactly(
                        new ProposalParticipant(family.child.profileId(), ProfileRole.CHILD, "주행자"),
                        new ProposalParticipant(family.parent.profileId(), ProfileRole.PARENT, "동반자"));
    }

    @Test
    @DisplayName("AI 장애 때 부모가 고른 힘이 있으면 측정 없는 만 3세도 그 요인의 연령대 영상으로 대체 편성한다")
    void AI_장애_때_고른_힘이_있으면_측정_없이도_대체_편성한다() {
        ProfileDetails toddler = family.addChild("막내", Fixed.TODAY.minusYears(3));
        CoachRun run = runningRun(toddler.profileId(), false, FitnessFactor.BALANCE);
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachProposalItem item = runs.findById(run.getId()).getProposals().getFirst();
        assertThat(item.title()).isEqualTo("평형성 키우기 20분");
        assertThat(item.video().videoId()).isEqualTo("sample00005");
        assertThat(item.citations())
                .singleElement()
                .extracting(ProposalCitation::chunkId)
                .isEqualTo("video:sample00005");
        assertThat(participantIds(item)).containsExactly(toddler.profileId());
    }

    @Test
    @DisplayName("편성 대상의 보호자 동의가 그 사이 거둬지면 AI 에 요청하지 않고 FAILED")
    void 대상의_보호자_동의가_거둬지면_AI_에_요청하지_않는다() {
        CoachRun run = runningRun();
        family.withdrawConsent(family.child.profileId());

        executor.execute(run.getId());

        assertThat(gateway.startRequests).isEmpty();
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("보호자 동의");
    }

    @Test
    @DisplayName("대체 편성 중 예외가 나도 RUNNING 으로 남기지 않고 FAILED 로 끝낸다")
    void 대체_편성_중_예외가_나도_FAILED_로_끝낸다() {
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            family.withdrawConsent(family.child.profileId()); // 요청을 만든 뒤 동의가 거둬졌다
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureReason()).startsWith("AI 장애 뒤 대체 편성도 실패");
    }

    @Test
    @DisplayName("AI 가 접수하는 사이 정리 작업이 FAILED 로 바꾼 실행은 폴링하지 않고, 정리 작업이 남긴 사유를 그대로 둔다")
    void 접수_사이_정리된_실행은_폴링하지_않는다() {
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            runs.failRunningCreatedBefore(Fixed.NOW.plusSeconds(1), "stale: 정리", Fixed.NOW);
            return new CoachRunAccepted("cr_late", "running", 1000);
        };

        executor.execute(run.getId());

        assertThat(gateway.pollCount).isZero();
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("stale: 정리");
        assertThat(saved.getAiRunId()).isNull();
    }

    @Test
    @DisplayName("AI 가 failed 를 돌려주면 FAILED, 400 도 FAILED")
    void AI_가_failed_를_돌려주면_FAILED() {
        CoachRun run = runningRun();
        gateway.onStart = request -> new CoachRunAccepted("cr_x", "running", 1000);
        gateway.onPoll = id -> new CoachRunResult(id, "failed", List.of(), null, false, null);

        executor.execute(run.getId());

        assertThat(runs.findById(run.getId()).getFailureReason()).isEqualTo("failed: AI run status=failed");

        CoachRun other = runningRun(family.child.profileId(), false, null);
        gateway.onStart = request -> {
            throw new AiBadRequestException("profile_refs");
        };
        executor.execute(other.getId());
        assertThat(runs.findById(other.getId()).getStatus()).isEqualTo(CoachRunStatus.FAILED);
    }

    private static ExerciseClip noisy(ExerciseClip quiet) {
        return new ExerciseClip(
                quiet.clipId(),
                quiet.videoId(),
                quiet.seq(),
                quiet.nameOnVideo(),
                quiet.exerciseName(),
                quiet.title(),
                quiet.factor(),
                quiet.phase(),
                quiet.startSec(),
                quiet.endSec(),
                quiet.homeOk(),
                false,
                quiet.needsProps(),
                quiet.isExercise(),
                quiet.ageGroup(),
                quiet.source(),
                quiet.active());
    }

    /** AI 가 일간 미션에 응원 부모를 참여자로 넣어 돌려준 경우(ai:coach/compose.py 일간 참여자 = 주행자 + 응원). */
    private CoachRunResult resultWithCheer(String id) {
        CoachRunResult.Session session = new CoachRunResult.Session(
                0, "본운동", 1, "운동", "유연성", 60, new CoachRunResult.Video("IdpXx2gm90o", 96, 156), List.of(1));
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                "일간",
                "오늘",
                Fixed.TODAY.toString(),
                Fixed.TODAY.toString(),
                List.of(
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자"),
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.cheerParent.profileId()), "응원")),
                20,
                60,
                List.of(session),
                "아이",
                "부모",
                "이유 [1].");
        return new CoachRunResult(
                id,
                "succeeded",
                List.of(),
                new CoachRunResult.Proposal(List.of(mission), List.of(new Citation(1, "처방", "p:1", null)), List.of()),
                false,
                null);
    }
}
