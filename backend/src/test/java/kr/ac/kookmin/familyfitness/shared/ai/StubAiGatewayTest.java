package kr.ac.kookmin.familyfitness.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest.Participant;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StubAiGatewayTest {
    private final StubAiGateway gateway = new StubAiGateway();
    private final AiProfile child = new AiProfile("p_child", 11, "세", "M", 140.0, 35.0, Map.of("012", 8.0));
    private final AiProfile parent = new AiProfile("p_parent", 41, "세", "F", null, null, Map.of());
    private final AiProfile cheer = new AiProfile("p_cheer", 43, "세", "M", null, null, Map.of());

    private CoachRunRequest request(Participant... participants) {
        return new CoachRunRequest(List.of(participants), "2026-09-07", 1, 3, 15);
    }

    @Test
    @DisplayName("startCoachRun 은 cr_ 식별자를 접수하고 getCoachRun 은 주행자마다 유연성 미션을 낸다")
    void startCoachRun_은_cr_식별자를_접수하고_getCoachRun_은_주행자마다_유연성_미션을_낸다() {
        CoachRunAccepted accepted = gateway.startCoachRun(
                request(new Participant(child, "주행자"), new Participant(parent, "동반자"), new Participant(cheer, "응원")));
        assertThat(accepted.runId()).startsWith("cr_");
        assertThat(accepted.status()).isEqualTo("running");

        CoachRunResult result = gateway.getCoachRun(accepted.runId());

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.isRunning()).isFalse();
        assertThat(result.refused()).isFalse();
        assertThat(result.steps().stream().map(CoachRunResult.Step::name).toList())
                .containsExactly("assess", "retrieve", "compose", "verify");
        assertThat(result.steps().stream().allMatch(it -> it.status().equals("ok")))
                .isTrue();
        assertThat(result.proposal().missions()).hasSize(1);
        CoachRunResult.Mission mission = result.proposal().missions().getFirst();
        assertThat(mission.title()).isEqualTo("같이 늘이는 한 주");
        assertThat(mission.startDate()).isEqualTo("2026-09-07");
        assertThat(mission.endDate()).isEqualTo("2026-09-13");
        assertThat(mission.participants().stream()
                        .map(CoachRunResult.ParticipantRef::ref)
                        .toList())
                .containsExactly("p_child", "p_parent");
        assertThat(mission.sessions().stream()
                        .map(CoachRunResult.Session::dayOffset)
                        .toList())
                .containsExactly(0, 2, 4);
        assertThat(mission.sessions().stream()
                        .map(CoachRunResult.Session::durationMin)
                        .toList())
                .containsOnly(15);
        assertThat(mission.sessions().getFirst().video()).isEqualTo(new CoachRunResult.Video("IdpXx2gm90o", 96));
        assertThat(mission.sessions().get(1).video()).isNull();
        assertThat(mission.sessions().getLast().video()).isEqualTo(new CoachRunResult.Video("IdpXx2gm90o", 96));
        assertThat(mission.sessions().getFirst().evidence()).containsExactly(1, 2);
        assertThat(mission.copyParent()).isNotBlank();
        assertThat(result.proposal().citations().stream().map(Citation::chunkId).toList())
                .containsExactly("prescription:유소년-11-F-0142", "video:IdpXx2gm90o");
    }

    @Test
    @DisplayName("주행자도 동반자도 없으면 refused")
    void 주행자도_동반자도_없으면_refused() {
        CoachRunAccepted accepted = gateway.startCoachRun(request(new Participant(cheer, "응원")));

        CoachRunResult result = gateway.getCoachRun(accepted.runId());

        assertThat(result.status()).isEqualTo("refused");
        assertThat(result.refused()).isTrue();
        assertThat(result.refusalReason()).isEqualTo("no_relevant_source");
        assertThat(result.proposal()).isNull();
    }

    @Test
    @DisplayName("모르는 run 은 RUN_NOT_FOUND")
    void 모르는_run_은_RUN_NOT_FOUND() {
        assertThat(assertThrows(AiRunNotFoundException.class, () -> gateway.getCoachRun("cr_nope"))
                        .getCode())
                .isEqualTo("RUN_NOT_FOUND");
    }

    @Test
    @DisplayName("ask 는 인용 있는 답을 주고 의료 질문은 medical_query 로 거부한다")
    void ask_는_인용_있는_답을_주고_의료_질문은_medical_query_로_거부한다() {
        CoachMessageResponse ok = gateway.ask(new CoachMessageRequest("p_child", "유소년", "유연성에 좋은 준비운동은?"));
        assertThat(ok.refused()).isFalse();
        assertThat(ok.answer()).endsWith("[1].");
        assertThat(ok.citations()).singleElement().extracting(Citation::index).isEqualTo(1);

        CoachMessageResponse refused = gateway.ask(new CoachMessageRequest("p_child", "유소년", "무릎 부상 후 운동해도 되나요?"));
        assertThat(refused.refused()).isTrue();
        assertThat(refused.refusalReason()).isEqualTo("medical_query");
        assertThat(refused.citations()).isEmpty();
    }

    @Test
    @DisplayName("trajectory 는 현재·+4·+8·+10 세 구간을 고정 notice 와 함께 돌려준다")
    void trajectory_는_현재_4_8_10_세_구간을_고정_notice_와_함께_돌려준다() {
        AiProfile measured = new AiProfile(
                child.profileRef(),
                child.age(),
                child.ageUnit(),
                child.sex(),
                child.heightCm(),
                child.weightKg(),
                Map.of("028", 40.0));
        TrajectoryResponse response = gateway.trajectory(new TrajectoryRequest(measured, "028", 10));

        assertThat(response.basis()).isEqualTo("cross_sectional_group_distribution");
        assertThat(response.itemCode()).isEqualTo("028");
        assertThat(response.notice()).isEqualTo(Copy.TRAJECTORY_NOTICE);
        assertThat(response.bands().stream().map(TrajectoryResponse.Band::age).toList())
                .containsExactly(11, 15, 19, 21);
        assertThat(response.bands().getFirst().p50()).isEqualTo(40.0);
        assertThat(response.bands().stream().allMatch(it -> it.p10() < it.p50() && it.p50() < it.p90()))
                .isTrue();
        assertThat(gateway.trajectory(new TrajectoryRequest(parent, "028", 5)).bands().stream()
                        .map(TrajectoryResponse.Band::age)
                        .toList())
                .containsExactly(41, 45);
    }

    @Test
    @DisplayName("assess 와 searchVideos 는 계약 모양의 단순 응답을 돌려준다")
    void assess_와_searchVideos_는_계약_모양의_단순_응답을_돌려준다() {
        AssessmentResponse assessment = gateway.assess(new AssessmentRequest(child));
        assertThat(assessment.inputLevel()).isEqualTo("L2");
        assertThat(assessment.ageGroup()).isEqualTo("유소년");
        assertThat(assessment.disclaimer()).isEqualTo(Copy.FITNESS_DISCLAIMER);
        assertThat(assessment.parentScope().factors())
                .singleElement()
                .extracting(AssessmentResponse.FactorScore::itemCode)
                .isEqualTo("012");

        VideoSearchResponse search = gateway.searchVideos(new VideoSearchRequest("유소년", List.of("유연성"), List.of(), 5));
        assertThat(search.hits())
                .singleElement()
                .extracting(VideoSearchResponse.Hit::videoId)
                .isEqualTo("IdpXx2gm90o");
        assertThat(search.hits().getFirst().citation().index()).isEqualTo(2);
    }
}
