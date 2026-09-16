package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType;
import kr.ac.kookmin.familyfitness.coaching.support.FakeAiGateway;
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunAccepted;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
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
    private final CoachRunPipeline pipeline = new CoachRunPipeline(
            runs,
            identity,
            fitness,
            videos,
            JsonMapper.builder().build(),
            Fixed.time(),
            new LabelBasedProposalPlanner(identity, fitness, videos));
    private final CoachRunExecutor executor = new CoachRunExecutor(pipeline, gateway, 0, 3);

    private FakeFitness newFitness() {
        FakeFitness fakeFitness = new FakeFitness();
        fakeFitness.measured(
                family.child.profileId(), new FakeFitness.Item("012", 8.0), new FakeFitness.Item("028", 45.0));
        return fakeFitness;
    }

    private CoachRun runningRun() {
        return runs.save(CoachRun.start(
                UUID.randomUUID(),
                family.familyId,
                Fixed.WEEK_START,
                TriggerType.MANUAL,
                3,
                15,
                family.parent.profileId(),
                Fixed.NOW));
    }

    @Test
    @DisplayName("succeeded 면 제안이 변환되어 AWAITING_APPROVAL 이 되고 AI 요청에는 이름 없이 역할만 실린다")
    void succeeded_면_제안이_변환되어_AWAITING_APPROVAL_이_되고_AI_요청에는_이름_없이_역할만_실린다() {
        CoachRun run = runningRun();

        executor.execute(run.getId());

        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(saved.getAiRunId()).startsWith("cr_");
        assertThat(saved.getSteps().stream().map(CoachStep::name).toList())
                .containsExactly("assess", "retrieve", "compose", "verify");
        assertThat(saved.getSummary()).contains("유연성");
        assertThat(saved.getProposalJson()).contains("같이 늘이는 한 주");
        assertThat(saved.getProposals()).hasSize(1);
        CoachProposalItem item = saved.getProposals().getFirst();
        assertThat(item.targetMetric()).isEqualTo("TIMER_MINUTES");
        assertThat(item.targetValue()).isEqualTo(45);
        assertThat(item.video().videoId()).isEqualTo("IdpXx2gm90o");
        assertThat(item.participants().stream()
                        .map(ProposalParticipant::profileId)
                        .toList())
                .containsExactlyInAnyOrder(family.child.profileId(), family.parent.profileId());
        assertThat(item.participants().stream()
                        .map(ProposalParticipant::coachRole)
                        .toList())
                .containsExactlyInAnyOrder("주행자", "동반자");
        assertThat(item.citations().stream().map(ProposalCitation::index).toList())
                .containsExactly(1, 2);

        assertThat(gateway.startRequests).hasSize(1);
        CoachRunRequest request = gateway.startRequests.getFirst();
        assertThat(request.startDate()).isEqualTo("2026-09-07");
        assertThat(request.profiles().stream()
                        .map(CoachRunRequest.Participant::role)
                        .toList())
                .containsExactlyInAnyOrder("동반자", "주행자", "응원");
        CoachRunRequest.Participant child = request.profiles().stream()
                .filter(it -> it.profile().profileRef().equals(ProfileRef.of(family.child.profileId())))
                .findFirst()
                .orElseThrow();
        assertThat(child.profile().age()).isEqualTo(11);
        assertThat(child.profile().measurements()).containsKeys("012", "028");
        assertThat(child.profile().inputLevel()).isEqualTo("L2");
        assertThat(request.toString()).doesNotContain("민준");
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
    @DisplayName("AI 장애인데 약점 판정이 없으면 FAILED 로 끝나고 상태 전이는 한 번만 일어난다")
    void AI_장애인데_약점_판정이_없으면_FAILED_로_끝나고_상태_전이는_한_번만_일어난다() {
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
    @DisplayName("AI 장애여도 측정 약점이 있으면 영상 라벨과 규준 근거로 대체 편성해 AWAITING_APPROVAL")
    void AI_장애여도_측정_약점이_있으면_영상_라벨과_규준_근거로_대체_편성해_AWAITING_APPROVAL() {
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
        assertThat(item.title()).contains("유연성");
        assertThat(item.targetMetric()).isEqualTo("TIMER_MINUTES");
        assertThat(item.targetValue()).isEqualTo(45);
        assertThat(item.video().videoId()).isEqualTo("IdpXx2gm90o");
        assertThat(item.citations().stream().map(ProposalCitation::label).toList())
                .anyMatch(it -> it.startsWith("국민체력100 규준"));
        assertThat(item.participants().stream()
                        .map(ProposalParticipant::coachRole)
                        .toList())
                .containsExactlyInAnyOrder("주행자", "동반자");
    }

    @Test
    @DisplayName("AI 가 failed 를 돌려주면 FAILED")
    void AI_가_failed_를_돌려주면_FAILED() {
        CoachRun run = runningRun();
        gateway.onStart = request -> new CoachRunAccepted("cr_x", "running", 1000);
        gateway.onPoll = id -> new CoachRunResult(id, "failed", List.of(), null, false, null);

        executor.execute(run.getId());

        assertThat(runs.findById(run.getId()).getFailureReason()).isEqualTo("failed: AI run status=failed");
    }
}
