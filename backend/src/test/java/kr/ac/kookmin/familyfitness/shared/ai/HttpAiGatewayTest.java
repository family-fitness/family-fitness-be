package kr.ac.kookmin.familyfitness.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 실제 HTTP 없이 {@link MockRestServiceServer} 로 와이어 형식과 오류 매핑을 본다. */
class HttpAiGatewayTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final HttpAiGateway gateway = new HttpAiGateway(
            builder,
            new AppProperties(
                    "Asia/Seoul",
                    "http://localhost:5173",
                    new AppProperties.Cors(),
                    new AppProperties.Auth(),
                    new AppProperties.Ai("http", "http://ai.internal:8000/")),
            readTimeout -> null,
            Duration.ZERO);
    private final AiProfile child = new AiProfile("p_abc", 11, "세", "M", 140.5, 35.0, Map.of("012", 8.0));

    @Test
    @DisplayName("startCoachRun 은 snake_case 본문을 보내고 202 접수를 매핑한다")
    void startCoachRun_은_snake_case_본문을_보내고_202_접수를_매핑한다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.profile_refs[0].ref").value("p_abc"))
                .andExpect(jsonPath("$.profile_refs[0].role").value("주행자"))
                .andExpect(jsonPath("$.profile_refs[0].age_unit").value("세"))
                .andExpect(jsonPath("$.profile_refs[0].input_level").value("L2"))
                .andExpect(jsonPath("$.profile_refs[0].height_cm").value(140.5))
                .andExpect(jsonPath("$.profile_refs[0].measurements.012").value(8.0))
                .andExpect(jsonPath("$.period.start_date").value("2026-09-07"))
                .andExpect(jsonPath("$.period.weeks").value(1))
                .andExpect(jsonPath("$.constraints.days_per_week").value(3))
                .andExpect(jsonPath("$.constraints.minutes_per_session").value(15))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"run_id\":\"cr_1\",\"status\":\"running\",\"poll_after_ms\":1500}"));

        CoachRunAccepted accepted = gateway.startCoachRun(
                new CoachRunRequest(List.of(new CoachRunRequest.Participant(child, "주행자")), "2026-09-07", 1, 3, 15));

        assertThat(accepted).isEqualTo(new CoachRunAccepted("cr_1", "running", 1500));
        server.verify();
    }

    @Test
    @DisplayName("409 오류 봉투는 RUN_IN_PROGRESS 예외가 된다")
    void 오류_봉투는_RUN_IN_PROGRESS_예외가_된다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"RUN_IN_PROGRESS\",\"message\":\"already running\"}}"));

        AiRunInProgressException e = assertThrows(
                AiRunInProgressException.class,
                () -> gateway.startCoachRun(new CoachRunRequest(
                        List.of(new CoachRunRequest.Participant(child, "주행자")), "2026-09-07", 1, 3, 15)));

        assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
        assertThat(e.getMessage()).isEqualTo("already running");
    }

    @Test
    @DisplayName("getCoachRun 은 succeeded proposal 을 도메인 DTO 로 매핑한다")
    void getCoachRun_은_succeeded_proposal_을_도메인_DTO_로_매핑한다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"run_id":"cr_1","status":"succeeded",
                         "steps":[{"seq":1,"name":"assess","status":"ok","summary":"측정 1명"}],
                         "proposal":{"missions":[{"title":"같이 늘이는 한 주","period":{"start_date":"2026-09-07","end_date":"2026-09-13"},
                           "participants":[{"ref":"p_abc","role":"주행자"}],
                           "sessions":[{"day_offset":0,"exercise_name":"앞으로 숙이기","fitness_factor":"유연성","duration_min":15,
                                        "video":{"video_id":"IdpXx2gm90o","start_sec":96},"evidence":[1]},
                                       {"day_offset":2,"exercise_name":"옆으로 숙이기","fitness_factor":"유연성","duration_min":15,"video":null,"evidence":[]}],
                           "copy":{"child":"해보자","parent":"부모 문구"}}],
                          "citations":[{"index":1,"label":"처방","chunk_id":"prescription:1"}]},
                         "refused":false,"refusal_reason":null,"extra_field":"ignored"}\
                        """, MediaType.APPLICATION_JSON));

        CoachRunResult result = gateway.getCoachRun("cr_1");

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.steps()).singleElement().isEqualTo(new CoachRunResult.Step(1, "assess", "ok", "측정 1명"));
        CoachRunResult.Mission mission = result.proposal().missions().getFirst();
        assertThat(result.proposal().missions()).hasSize(1);
        assertThat(mission.startDate()).isEqualTo("2026-09-07");
        assertThat(mission.participants()).singleElement().isEqualTo(new CoachRunResult.ParticipantRef("p_abc", "주행자"));
        assertThat(mission.sessions().getFirst().video()).isEqualTo(new CoachRunResult.Video("IdpXx2gm90o", 96));
        assertThat(mission.sessions().getLast().video()).isNull();
        assertThat(mission.sessions().stream()
                        .mapToInt(CoachRunResult.Session::durationMin)
                        .sum())
                .isEqualTo(30);
        assertThat(mission.copyParent()).isEqualTo("부모 문구");
        assertThat(result.proposal().citations())
                .singleElement()
                .isEqualTo(new Citation(1, "처방", "prescription:1", null));
    }

    @Test
    @DisplayName("404 는 RUN_NOT_FOUND 다")
    void 는_RUN_NOT_FOUND_다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_x"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"RUN_NOT_FOUND\",\"message\":\"no\"}}"));

        assertThat(assertThrows(AiRunNotFoundException.class, () -> gateway.getCoachRun("cr_x"))
                        .getCode())
                .isEqualTo("RUN_NOT_FOUND");
    }

    @Test
    @DisplayName("ask 는 profile_ref·age_group·question 을 보내고 답·인용·거부를 매핑한다")
    void ask_는_profile_ref_age_group_question_을_보내고_답_인용_거부를_매핑한다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.profile_ref").value("p_abc"))
                .andExpect(jsonPath("$.age_group").value("유소년"))
                .andExpect(jsonPath("$.question").value("질문"))
                .andRespond(withSuccess(
                        "{\"answer\":\"답 [1]\",\"citations\":[{\"index\":1,\"label\":\"처방\",\"chunk_id\":\"c1\",\"url\":\"https://x\"}],\"refused\":false,\"refusal_reason\":null}",
                        MediaType.APPLICATION_JSON));

        CoachMessageResponse response = gateway.ask(new CoachMessageRequest("p_abc", "유소년", "질문"));

        assertThat(response.answer()).isEqualTo("답 [1]");
        assertThat(response.citations()).singleElement().isEqualTo(new Citation(1, "처방", "c1", "https://x"));
        assertThat(response.refused()).isFalse();
    }

    @Test
    @DisplayName("503 은 AiUnavailableException 이고 messages 는 재시도하지 않는다")
    void 은_AiUnavailableException_이고_messages_는_재시도하지_않는다() {
        server.expect(ExpectedCount.once(), requestTo("http://ai.internal:8000/v1/coach/messages"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"TEMPORARILY_UNAVAILABLE\",\"message\":\"llm down\"}}"));

        AiUnavailableException e = assertThrows(
                AiUnavailableException.class, () -> gateway.ask(new CoachMessageRequest("p_abc", "유소년", "질문")));

        assertThat(e.getCode()).isEqualTo("TEMPORARILY_UNAVAILABLE");
        assertThat(e.getMessage()).contains("llm down");
        server.verify();
    }

    @Test
    @DisplayName("assessment 는 503 에 두 번 재시도하고 세 번째 성공을 돌려준다")
    void assessment_는_503_에_두_번_재시도하고_세_번째_성공을_돌려준다() {
        server.expect(ExpectedCount.times(2), requestTo("http://ai.internal:8000/v1/fitness/assessment"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo("http://ai.internal:8000/v1/fitness/assessment"))
                .andExpect(jsonPath("$.profile_ref").value("p_abc"))
                .andExpect(jsonPath("$.age_unit").value("세"))
                .andRespond(withSuccess("""
                        {"input_level":"L2","age_group":"유소년","child_scope":{"focus_one":{"factor":"유연성","copy":"키우기 좋은 영역"}},
                            "parent_scope":{"grade":"3등급","peer_distribution":[{"grade":"1등급","ratio":0.1}],
                            "factors":[{"factor":"유연성","item_code":"012","item_name":"앉아윗몸앞으로굽히기","item_label":"앉아윗몸앞으로굽히기","unit":"cm","value":8.0,"score":50.0,"percentile":48,"band":"steady","n":120}],
                            "copy":{"strength":"a","focus":"b"}},"low_sample":false,"disclaimer":"참고"}\
                        """, MediaType.APPLICATION_JSON));

        AssessmentResponse response = gateway.assess(new AssessmentRequest(child));

        assertThat(response.inputLevel()).isEqualTo("L2");
        assertThat(response.childScope().focusOne().factor()).isEqualTo("유연성");
        assertThat(response.parentScope().grade()).isEqualTo("3등급");
        assertThat(response.parentScope().factors())
                .singleElement()
                .extracting(AssessmentResponse.FactorScore::percentile)
                .isEqualTo(48);
        assertThat(response.parentScope().copy()).containsEntry("focus", "b");
        server.verify();
    }

    @Test
    @DisplayName("400 은 재시도 없이 AI_BAD_REQUEST 이고 trajectory 는 item_code·horizon_years 를 보낸다")
    void 은_재시도_없이_AI_BAD_REQUEST_이고_trajectory_는_item_code_horizon_years_를_보낸다() {
        server.expect(ExpectedCount.once(), requestTo("http://ai.internal:8000/v1/fitness/trajectory"))
                .andExpect(jsonPath("$.item_code").value("028"))
                .andExpect(jsonPath("$.horizon_years").value(10))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"ITEM_NOT_ALLOWED\",\"message\":\"005\"}}"));

        AiBadRequestException e = assertThrows(
                AiBadRequestException.class, () -> gateway.trajectory(new TrajectoryRequest(child, "028", 10)));

        assertThat(e.getCode()).isEqualTo("AI_BAD_REQUEST");
        assertThat(e.getMessage()).contains("ITEM_NOT_ALLOWED");
        server.verify();
    }
}
