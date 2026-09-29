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
    private final AiProfile toddler = new AiProfile("p_toddler", 40, "개월", "F", null, null, Map.of());

    private CoachRunRequest request(Participant... participants) {
        return request(null, participants);
    }

    private CoachRunRequest request(String focusFactor, Participant... participants) {
        return new CoachRunRequest(
                List.of(participants),
                "2026-09-09",
                1,
                new CoachRunRequest.Constraints(1, 20, null, true, true, true, focusFactor, false));
    }

    @Test
    @DisplayName("startCoachRun 은 cr_ 식별자를 접수하고 getCoachRun 은 주행자에게 그날 하루짜리 미션 하나를 낸다")
    void startCoachRun_은_cr_식별자를_접수하고_getCoachRun_은_주행자에게_하루짜리_미션을_낸다() {
        CoachRunAccepted accepted = gateway.startCoachRun(request(new Participant(child, "주행자")));
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
        assertThat(mission.kind()).isEqualTo("일간");
        assertThat(mission.title()).isEqualTo("유연성 키우기 20분");
        assertThat(mission.startDate()).isEqualTo("2026-09-09");
        assertThat(mission.endDate()).isEqualTo("2026-09-09");
        assertThat(mission.participants()).containsExactly(new CoachRunResult.ParticipantRef("p_child", "주행자"));
        assertThat(mission.sessions().stream()
                        .map(CoachRunResult.Session::dayOffset)
                        .toList())
                .containsOnly(0);
        // AI 가짓수 규칙: 20분이면 준비 2 · 본 4 · 정리 1. 칸마다 분은 싣지 않는다(AI 처럼) — duration_sec 는 클립 길이다
        assertThat(mission.sessions().stream()
                        .map(CoachRunResult.Session::phase)
                        .toList())
                .containsExactly("준비운동", "준비운동", "본운동", "본운동", "본운동", "본운동", "정리운동");
        assertThat(mission.sessions().stream()
                        .map(CoachRunResult.Session::order)
                        .toList())
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(mission.sessions().stream()
                        .map(CoachRunResult.Session::durationSec)
                        .toList())
                .containsExactly(38, 38, 34, 52, 34, 74, 46);
        assertThat(mission.durationMin()).isEqualTo(20);
        assertThat(mission.videoSec()).isEqualTo(316);
        assertThat(mission.reason()).endsWith("[1].");
        // 영상 구간은 V132 클립 표의 실제 경계다
        assertThat(mission.sessions().getFirst().exerciseName()).isEqualTo("넙다리 안쪽 늘리기 (나비자세)");
        assertThat(mission.sessions().getFirst().video()).isEqualTo(new CoachRunResult.Video("Eg3GpTv7z8s", 144, 182));
        assertThat(mission.sessions().getLast().video()).isEqualTo(new CoachRunResult.Video("Eg3GpTv7z8s", 1376, 1422));
        assertThat(mission.sessions().getFirst().evidence()).containsExactly(1, 2);
        assertThat(mission.copyParent()).isEqualTo("유연성은 매일 조금씩 늘려 가는 영역입니다. 오늘 20분이면 충분합니다.");
        assertThat(result.proposal().citations().stream().map(Citation::chunkId).toList())
                .containsExactly("prescription:유소년-11-F-0142", "video:Eg3GpTv7z8s");
        assertThat(result.proposal().notices()).isEmpty();
    }

    @Test
    @DisplayName("칸 가짓수는 AI 규칙을 따른다 — 10분 1·3·1, 30분 2·5·2, 40분 3·6·3")
    void 칸_가짓수는_AI_규칙을_따른다() {
        for (int[] expected : new int[][] {{10, 1, 3, 1}, {30, 2, 5, 2}, {40, 3, 6, 3}}) {
            CoachRunRequest request = new CoachRunRequest(
                    List.of(new Participant(child, "주행자")),
                    "2026-09-09",
                    1,
                    new CoachRunRequest.Constraints(1, expected[0], null, true, true, true, null, false));
            List<String> phases = gateway
                    .getCoachRun(gateway.startCoachRun(request).runId())
                    .proposal()
                    .missions()
                    .getFirst()
                    .sessions()
                    .stream()
                    .map(CoachRunResult.Session::phase)
                    .toList();

            assertThat(phases.stream().filter("준비운동"::equals)).hasSize(expected[1]);
            assertThat(phases.stream().filter("본운동"::equals)).hasSize(expected[2]);
            assertThat(phases.stream().filter("정리운동"::equals)).hasSize(expected[3]);
        }
    }

    @Test
    @DisplayName("고른 힘은 미션 요인이 되고, 시드 영상 연령(7~12세) 밖의 주행자에게는 영상을 붙이지 않는다")
    void 고른_힘은_미션_요인이_되고_연령_밖이면_영상을_붙이지_않는다() {
        CoachRunResult result =
                gateway.getCoachRun(gateway.startCoachRun(request("민첩성", new Participant(toddler, "주행자")))
                        .runId());

        CoachRunResult.Mission mission = result.proposal().missions().getFirst();
        assertThat(mission.title()).isEqualTo("민첩성 키우기 20분");
        assertThat(mission.sessions().stream()
                        .map(CoachRunResult.Session::fitnessFactor)
                        .toList())
                .containsOnly("민첩성");
        assertThat(mission.sessions().stream().map(CoachRunResult.Session::video))
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("성인 · 어르신 주행자에게는 성인 영상 구간을 붙이고, 그 영상 인용을 뒤에 더한다")
    void 성인_어르신_주행자에게는_성인_영상_구간을_붙인다() {
        AiProfile grandma = new AiProfile("p_grandma", 70, "세", "F", null, null, Map.of());

        CoachRunResult result = gateway.getCoachRun(
                gateway.startCoachRun(request(new Participant(child, "주행자"), new Participant(grandma, "주행자")))
                        .runId());

        CoachRunResult.Mission senior = result.proposal().missions().get(1);
        assertThat(senior.participants()).containsExactly(new CoachRunResult.ParticipantRef("p_grandma", "주행자"));
        assertThat(senior.sessions().getFirst().exerciseName()).isEqualTo("손목, 발목 돌리기");
        assertThat(senior.sessions().getFirst().video()).isEqualTo(new CoachRunResult.Video("IhShIA-WJNE", 20, 50));
        assertThat(senior.sessions().stream().map(CoachRunResult.Session::video))
                .allMatch(it -> it != null && it.videoId().equals("IhShIA-WJNE"));
        assertThat(senior.sessions().getFirst().evidence()).containsExactly(1, 3);
        assertThat(result.proposal().missions().getFirst().sessions().getFirst().evidence())
                .containsExactly(1, 2);
        assertThat(result.proposal().citations().stream().map(Citation::chunkId).toList())
                .containsExactly("prescription:유소년-11-F-0142", "video:Eg3GpTv7z8s", "video:IhShIA-WJNE");
    }

    @Test
    @DisplayName("주행자가 없으면 refused")
    void 주행자가_없으면_refused() {
        CoachRunAccepted accepted =
                gateway.startCoachRun(request(new Participant(parent, "동반자"), new Participant(cheer, "응원")));

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
