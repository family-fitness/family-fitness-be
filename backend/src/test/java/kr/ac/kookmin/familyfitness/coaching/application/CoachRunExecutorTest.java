package kr.ac.kookmin.familyfitness.coaching.application;

import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.COOLDOWN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.MAIN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.WARMUP;
import static kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseClipRepository.clip;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunFailureCode;
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
import kr.ac.kookmin.familyfitness.shared.ai.AiRunNotFoundException;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
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
    private final CoachRunExecutor executor = new CoachRunExecutor(pipeline, gateway, new SyncTaskExecutor(), 0, 3);

    /** 측정은 있지만 요인 백분위(약점 · 강점)가 없다 — 대체 편성할 근거가 없는 기본 상태. */
    private FakeFitness newFitness() {
        FakeFitness fakeFitness = new FakeFitness();
        fakeFitness.measured(
                family.child.profileId(), new FakeFitness.Item("012", 8.0), new FakeFitness.Item("028", 45.0));
        return fakeFitness;
    }

    /** 유연성이 약점(백분위 24)이라 대체 편성이 유연성 미션을 낼 수 있다. */
    private void measuredWithWeakness() {
        fitness.measured(
                family.child.profileId(),
                new FactorPoint(FitnessFactor.FLEXIBILITY, "012", 24),
                new FactorPoint(FitnessFactor.CARDIO, "020", 80),
                new FakeFitness.Item("012", 8.0));
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

    /** 대체 편성으로 낸 제안이다 — 두 번째 단계가 「AI 서비스 장애(까닭) → 영상 라벨 기반 편성」 partial 이다. */
    private static void assertFallback(CoachRun saved, String summary) {
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getFailureCode()).isNull();
        assertThat(saved.getSteps().stream().map(CoachStep::status).toList())
                .containsExactly("ok", "partial", "ok", "ok");
        assertThat(saved.getSteps().get(1).summary()).startsWith("AI 서비스 장애(" + summary + ")");
        assertThat(saved.getProposals())
                .singleElement()
                .extracting(CoachProposalItem::title)
                .isEqualTo("유연성 키우기 20분");
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
        assertThat(saved.getFailureCode()).isNull();
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
    @DisplayName("AI 장애 때 어르신은 어르신 클립과 성인 클립에서 고른다 — 어르신 라벨 클립이 먼저")
    void AI_장애_때_어르신은_어르신_클립과_성인_클립에서_고른다() {
        ProfileDetails grandma = family.addChild("할머니", Fixed.TODAY.minusYears(70));
        List.of(
                        clip("adult", 10, 70, "손목 돌리기", WARMUP, FitnessFactor.FLEXIBILITY, AgeGroup.ADULT),
                        clip("adult", 80, 140, "앉았다 일어서기", MAIN, FitnessFactor.STRENGTH, AgeGroup.ADULT),
                        clip("adult", 150, 220, "벽 밀기", MAIN, FitnessFactor.STRENGTH, AgeGroup.ADULT),
                        clip("senior", 0, 60, "의자 잡고 일어서기", MAIN, FitnessFactor.STRENGTH, AgeGroup.SENIOR),
                        clip("adult", 220, 280, "목 늘리기", COOLDOWN, FitnessFactor.FLEXIBILITY, AgeGroup.ADULT),
                        // 다른 연령대는 고르지 않는다
                        clip("youth", 0, 60, "버피", MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH))
                .forEach(it -> clips.clips.put(it.clipId(), it));
        CoachRun run = runningRun(grandma.profileId(), false, FitnessFactor.STRENGTH);
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getProposals().getFirst().sessions().stream()
                        .map(MissionSession::title)
                        .toList())
                .containsExactly("손목 돌리기", "의자 잡고 일어서기", "앉았다 일어서기", "벽 밀기", "목 늘리기");
    }

    @Test
    @DisplayName("AI 장애 때 요인도 백분위도 없으면(측정 전 · 만 7~10세) 그 연령대 클립으로 전신 미션을 짠다 — AI 규칙 편성과 같다")
    void AI_장애_때_요인도_백분위도_없으면_그_연령대_클립으로_전신_미션을_짠다() {
        // 기본 측정은 백분위가 없다(만 7~10세처럼). 키워 주고 싶은 역량도 없다
        String eg = "Eg3GpTv7z8s";
        List.of(
                        clip(eg, 144, 182, "나비자세", WARMUP, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip(eg, 500, 560, "팔 펴기", MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH),
                        clip(eg, 600, 640, "제자리 걷기", MAIN, null, AgeGroup.YOUTH),
                        clip(eg, 1426, 1466, "다리 뒤 늘리기", COOLDOWN, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                        clip("toddler", 0, 60, "유아 늘이기", MAIN, FitnessFactor.FLEXIBILITY, AgeGroup.TODDLER))
                .forEach(it -> clips.clips.put(it.clipId(), it));
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getFailureCode()).isNull();
        assertThat(saved.getSteps().get(0).summary()).isEqualTo("측정 있음 · 짚을 요인 없음 → 전신");
        assertThat(saved.getSteps().get(1).summary()).endsWith("클립 라벨 기반 편성 · 클립 4개");
        CoachProposalItem item = saved.getProposals().getFirst();
        assertThat(item.title()).isEqualTo("전신 기르기 20분");
        assertThat(item.sessions().stream().map(MissionSession::title).toList())
                .containsExactly("나비자세", "팔 펴기", "제자리 걷기", "다리 뒤 늘리기");
        assertThat(item.sessions().get(2).factor()).isNull();
        assertThat(item.citations().stream().map(ProposalCitation::chunkId).toList())
                .containsExactly("video:" + eg);
        assertThat(participantIds(item)).containsExactly(family.child.profileId(), family.parent.profileId());

        // 측정 전인 만 3세도 그 연령대(유아기) 클립으로 짠다
        ProfileDetails toddler = family.addChild("막내", Fixed.TODAY.minusYears(3));
        CoachRun toddlerRun = runningRun(toddler.profileId(), false, null);
        executor.execute(toddlerRun.getId());

        CoachRun toddlerSaved = runs.findById(toddlerRun.getId());
        assertThat(toddlerSaved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(toddlerSaved.getSteps().get(0).summary()).isEqualTo("측정 없음 · 짚을 요인 없음 → 전신");
        assertThat(toddlerSaved.getProposals().getFirst().sessions().stream()
                        .map(MissionSession::title)
                        .toList())
                .containsExactly("유아 늘이기");
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
    @DisplayName("보호자가 키워 주고 싶은 역량은 focus_factor 로 AI 에 실리고 스텁 제안의 요인이 된다")
    void 부모가_고른_힘은_focus_factor_로_실린다() {
        CoachRun run = runningRun(family.child.profileId(), false, FitnessFactor.AGILITY);

        executor.execute(run.getId());

        assertThat(gateway.startRequests.getFirst().constraints().focusFactor()).isEqualTo("민첩성");
        assertThat(runs.findById(run.getId()).getProposals().getFirst().title()).isEqualTo("민첩성 키우기 20분");
    }

    @Test
    @DisplayName("refused 면 대체 편성 근거가 있어도 FAILED(NO_CITATIONS)이고 원문은 refused 접두어로 남는다")
    void refused_면_FAILED_NO_CITATIONS_이고_대체_편성하지_않는다() {
        measuredWithWeakness();
        CoachRun run = runningRun();
        gateway.onPoll = id -> new CoachRunResult(
                id,
                "refused",
                List.of(new CoachRunResult.Step(1, "retrieve", "refused", "근거 0건")),
                null,
                true,
                "no_relevant_source");

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.NO_CITATIONS);
        assertThat(saved.getFailureReason()).isEqualTo("refused: no_relevant_source");
        assertThat(saved.isAiRefused()).isTrue();
        assertThat(saved.getAiRefusalReason()).isEqualTo("no_relevant_source");
        assertThat(saved.getSteps())
                .singleElement()
                .extracting(CoachStep::status)
                .isEqualTo("refused");
        assertThat(saved.getProposals()).isEmpty();
        assertThat(saved.lockKey()).isNull();
    }

    @Test
    @DisplayName("running 이 계속되면 최대 횟수까지 폴링하고 대체 편성으로 넘기며, 근거도 없으면 FAILED(AI_FAILED)")
    void 폴링이_만료되면_대체_편성으로_넘기고_근거가_없으면_AI_FAILED() {
        CoachRun run = runningRun();
        gateway.onPoll = id -> new CoachRunResult(id, "running", List.of(), null, false, null);

        executor.execute(run.getId());

        assertThat(gateway.pollCount).isEqualTo(3);
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.AI_FAILED);
        assertThat(saved.getFailureReason()).contains("timeout").contains("대체 편성 근거");
        assertThat(saved.lockKey()).isNull();
    }

    @Test
    @DisplayName("폴링이 만료돼도 측정 약점이 있으면 대체 편성해 AWAITING_APPROVAL")
    void 폴링이_만료돼도_근거가_있으면_대체_편성한다() {
        measuredWithWeakness();
        CoachRun run = runningRun();
        gateway.onPoll = id -> new CoachRunResult(id, "running", List.of(), null, false, null);

        executor.execute(run.getId());

        assertFallback(runs.findById(run.getId()), "시간 초과");
    }

    @Test
    @DisplayName("AI 가 failed 를 주면 대체 편성으로 넘기고, 근거가 없으면 AI 의 단계를 남긴 채 FAILED(AI_FAILED)")
    void AI_가_failed_를_주면_대체_편성으로_넘긴다() {
        measuredWithWeakness();
        CoachRun withBasis = runningRun();
        gateway.onStart = request -> new CoachRunAccepted("cr_x", "running", 1000);
        gateway.onPoll = id -> new CoachRunResult(
                id, "failed", List.of(new CoachRunResult.Step(1, "assess", "failed", "임베딩 서버 없음")), null, false, null);

        executor.execute(withBasis.getId());

        CoachRun fallback = runs.findById(withBasis.getId());
        assertFallback(fallback, "편성 실패");
        assertThat(fallback.getAiRunId()).isEqualTo("cr_x");

        ProfileDetails toddler = family.addChild("막내", Fixed.TODAY.minusYears(3)); // 측정도 키워 주고 싶은 역량도 없다
        CoachRun noBasis = runningRun(toddler.profileId(), false, null);
        executor.execute(noBasis.getId());

        CoachRun failed = runs.findById(noBasis.getId());
        assertThat(failed.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo(CoachRunFailureCode.AI_FAILED);
        assertThat(failed.getFailureReason()).contains("failed: AI run status=failed");
        assertThat(failed.getSteps())
                .singleElement()
                .extracting(CoachStep::summary)
                .isEqualTo("임베딩 서버 없음");
    }

    @Test
    @DisplayName("폴링이 404 RUN_NOT_FOUND(AI 재시작 · 다른 워커)면 대체 편성으로 넘긴다")
    void 폴링이_404_면_대체_편성으로_넘긴다() {
        measuredWithWeakness();
        CoachRun run = runningRun();
        gateway.onPoll = id -> {
            throw new AiRunNotFoundException(id);
        };

        executor.execute(run.getId());

        assertThat(gateway.pollCount).isEqualTo(1);
        assertFallback(runs.findById(run.getId()), "실행 없음");
    }

    @Test
    @DisplayName("succeeded 인데 제안이 없으면 AI 실행 실패로 보고 대체 편성으로 넘긴다")
    void 제안_없는_succeeded_는_대체_편성으로_넘긴다() {
        measuredWithWeakness();
        CoachRun run = runningRun();
        gateway.onPoll = id -> new CoachRunResult(id, "succeeded", List.of(), null, false, null);

        executor.execute(run.getId());

        assertFallback(runs.findById(run.getId()), "편성 실패");
    }

    @Test
    @DisplayName("폴링 한 번의 일시 오류(타임아웃 · 503)는 다음 폴링으로 넘기고, 그다음 AI 결과를 그대로 쓴다")
    void 폴링_한_번의_일시_오류는_다음_폴링으로_넘긴다() {
        CoachRun run = runningRun(); // 대체 편성 근거가 없으니 대체 편성으로 갔다면 FAILED 가 된다
        StubResultOnSecondPoll poll = new StubResultOnSecondPoll();
        gateway.onPoll = poll::next;

        executor.execute(run.getId());

        assertThat(gateway.pollCount).isEqualTo(2);
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getSteps().stream().map(CoachStep::status).toList()).doesNotContain("partial");
        assertThat(saved.getProposals())
                .singleElement()
                .extracting(CoachProposalItem::title)
                .isEqualTo("오늘");
    }

    @Test
    @DisplayName("폴링이 끝까지 일시 오류면 만료와 같게 대체 편성으로 넘긴다")
    void 폴링이_끝까지_일시_오류면_대체_편성으로_넘긴다() {
        measuredWithWeakness();
        CoachRun run = runningRun();
        gateway.onPoll = id -> {
            throw new AiUnavailableException("AI 연결 실패·타임아웃: GET coach/runs/" + id);
        };

        executor.execute(run.getId());

        assertThat(gateway.pollCount).isEqualTo(3);
        assertFallback(runs.findById(run.getId()), "시간 초과");
    }

    @Test
    @DisplayName("AI 에 닿지 못했는데 키워 주고 싶은 역량도 측정 약점도 없으면 FAILED(AI_FAILED)로 끝나고 상태 전이는 한 번만 일어난다")
    void AI_장애인데_근거가_없으면_FAILED_로_끝난다() {
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.AI_FAILED);
        assertThat(saved.getFailureReason()).contains("unavailable: 연결 실패");

        executor.execute(run.getId());
        assertThat(runs.findById(run.getId()).getStatus()).isEqualTo(CoachRunStatus.FAILED);
    }

    @Test
    @DisplayName("AI 장애여도 측정 약점이 있으면 대상 아이의 그날 하루짜리로 대체 편성해 AWAITING_APPROVAL")
    void AI_장애여도_측정_약점이_있으면_하루짜리로_대체_편성한다() {
        measuredWithWeakness();
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertFallback(saved, "연결 실패");
        assertThat(saved.getAiRunId()).isNull();
        CoachProposalItem item = saved.getProposals().getFirst();
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
    @DisplayName("AI 장애 때 보호자가 키워 주고 싶은 역량이 있으면 측정 없는 만 3세도 그 요인의 연령대 영상으로 대체 편성한다")
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
    @DisplayName("편성 대상의 보호자 동의가 그 사이 거둬지면 AI 에 요청하지 않고 FAILED(CONSENT_REQUIRED)")
    void 대상의_보호자_동의가_거둬지면_AI_에_요청하지_않는다() {
        CoachRun run = runningRun();
        family.withdrawConsent(family.child.profileId());

        executor.execute(run.getId());

        assertThat(gateway.startRequests).isEmpty();
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.CONSENT_REQUIRED);
        assertThat(saved.getFailureReason()).contains("보호자 동의");
    }

    @Test
    @DisplayName("대체 편성 중 예외가 나도 RUNNING 으로 남기지 않고 FAILED 로 끝낸다 — 동의가 거둬진 탓이면 CONSENT_REQUIRED")
    void 대체_편성_중_예외가_나도_FAILED_로_끝낸다() {
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            family.withdrawConsent(family.child.profileId()); // 요청을 만든 뒤 동의가 거둬졌다
            throw new AiUnavailableException("연결 실패");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.CONSENT_REQUIRED);
        assertThat(saved.getFailureReason()).startsWith("AI 로 짜지 못한 뒤 대체 편성도 실패");
    }

    @Test
    @DisplayName("AI 가 접수하는 사이 정리 작업이 FAILED(STALE)로 바꾼 실행은 폴링하지 않고, 정리 작업이 남긴 사유를 그대로 둔다")
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
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.STALE);
        assertThat(saved.getFailureReason()).isEqualTo("stale: 정리");
        assertThat(saved.getAiRunId()).isNull();
    }

    @Test
    @DisplayName("대기열에서 기다리는 사이 정리 작업이 끝낸 실행은 AI 를 부르지 않는다")
    void 대기_중_정리된_실행은_AI_를_부르지_않는다() {
        CoachRun run = runningRun();
        runs.failRunningCreatedBefore(Fixed.NOW.plusSeconds(1), "stale: 정리", Fixed.NOW);

        executor.execute(run.getId());

        assertThat(gateway.startRequests).isEmpty();
        assertThat(runs.findById(run.getId()).getFailureCode()).isEqualTo(CoachRunFailureCode.STALE);
    }

    @Test
    @DisplayName("AI 400 은 이쪽 결함이라 대체 편성하지 않고 FAILED(ERROR)")
    void AI_400_은_FAILED_ERROR() {
        measuredWithWeakness();
        CoachRun run = runningRun();
        gateway.onStart = request -> {
            throw new AiBadRequestException("profile_refs");
        };

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.ERROR);
        assertThat(saved.getProposals()).isEmpty();
    }

    @Test
    @DisplayName("편성 스레드와 대기열이 가득 차 받지 못하면 곧바로 FAILED(BUSY)이고 잠금을 푼다")
    void 스레드와_대기열이_가득_차면_FAILED_BUSY() {
        CoachRunExecutor full = new CoachRunExecutor(
                pipeline,
                gateway,
                task -> {
                    throw new TaskRejectedException("가득 참");
                },
                0,
                3);
        CoachRun run = runningRun();

        full.on(new CoachRunRequested(run.getId()));

        assertThat(gateway.startRequests).isEmpty();
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.BUSY);
        assertThat(saved.lockKey()).isNull();
    }

    @Test
    @DisplayName("커밋 뒤 이벤트는 편성 스레드 풀에 넘겨 실행한다")
    void 커밋_뒤_이벤트는_편성_스레드_풀에서_실행한다() {
        CoachRun run = runningRun();

        executor.on(new CoachRunRequested(run.getId()));

        assertThat(runs.findById(run.getId()).getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
    }

    @Test
    @DisplayName("AI 가 영상 표에 없는 공단 영상을 고르면 그 칸은 영상 없이 저장한다 — 유튜브로 틀려다 실패하지 않게. 표에 있는 공단 영상 · 유튜브 칸은 그대로")
    void 영상_표에_없는_공단_영상_칸은_영상_없이_저장한다() {
        videos.videos.put("0AUDLJ08S_00351", Videos.kspo("0AUDLJ08S_00351", "팔굽혀펴기", 91, 7, 12));
        CoachRun run = runningRun();
        gateway.onPoll = id -> resultWithVideos(
                id,
                new CoachRunResult.Video("0AUDLJ08S_99999", 0, 80, "kspo", Videos.kspoMp4("0AUDLJ08S_99999")),
                new CoachRunResult.Video("0AUDLJ08S_00351", 0, 91, "kspo", Videos.kspoMp4("0AUDLJ08S_00351")),
                new CoachRunResult.Video("IdpXx2gm90o", 96, 156));

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        CoachProposalItem item = saved.getProposals().getFirst();
        assertThat(item.sessions()).hasSize(3);
        assertThat(item.sessions().get(0).clip()).isNull();
        assertThat(item.sessions().get(0).title()).isEqualTo("운동1");
        assertThat(item.sessions().get(1).clip()).isEqualTo(new SessionClip("0AUDLJ08S_00351", 0, 91, "팔굽혀펴기"));
        assertThat(item.sessions().get(2).clip()).isEqualTo(new SessionClip("IdpXx2gm90o", 96, 156, "영상 IdpXx2gm90o"));
        // 제안 대표 영상도 틀 수 없는 공단 영상을 건너뛴다
        assertThat(item.video()).isEqualTo(new ProposalVideo("0AUDLJ08S_00351", 0));
    }

    /** 본운동 칸마다 영상 하나. 칸 이름은 운동1, 운동2 … */
    private CoachRunResult resultWithVideos(String id, CoachRunResult.Video... videos) {
        List<CoachRunResult.Session> sessions = new java.util.ArrayList<>();
        for (int i = 0; i < videos.length; i++) {
            sessions.add(new CoachRunResult.Session(0, "본운동", i + 1, "운동" + (i + 1), "근력", 60, videos[i], List.of(1)));
        }
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                "일간",
                "오늘",
                Fixed.TODAY.toString(),
                Fixed.TODAY.toString(),
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자")),
                20,
                180,
                List.copyOf(sessions),
                "아이",
                "부모",
                "이유 [1].");
        return new CoachRunResult(
                id,
                "succeeded",
                List.of(new CoachRunResult.Step(1, "assess", "ok", "측정 있음")),
                new CoachRunResult.Proposal(List.of(mission), List.of(new Citation(1, "처방", "p:1", null)), List.of()),
                false,
                null);
    }

    /** 첫 폴링은 일시 오류, 두 번째는 대상 아이 하루짜리 succeeded. */
    private final class StubResultOnSecondPoll {
        private final AtomicInteger calls = new AtomicInteger();

        CoachRunResult next(String id) {
            if (calls.incrementAndGet() == 1) throw new AiUnavailableException("AI 503 (GET coach/runs/" + id + ")");
            return resultWithCheer(id);
        }
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
                List.of(new CoachRunResult.Step(1, "assess", "ok", "측정 있음")),
                new CoachRunResult.Proposal(List.of(mission), List.of(new Citation(1, "처방", "p:1", null)), List.of()),
                false,
                null);
    }
}
