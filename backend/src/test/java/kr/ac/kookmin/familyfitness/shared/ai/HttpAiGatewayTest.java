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

    /** 대상 아이 한 명의 그날 하루(20분 · 조용히 · 집 · 도구 없음 · 민첩성 · 보호자 같이). */
    private CoachRunRequest dailyRequest() {
        return new CoachRunRequest(
                List.of(new CoachRunRequest.Participant(child, "주행자")),
                "2026-09-09",
                1,
                new CoachRunRequest.Constraints(1, 20, null, true, true, true, "민첩성", true));
    }

    @Test
    @DisplayName(
            "startCoachRun 은 snake_case 본문을 보내고 202 접수를 매핑한다 — 조건 칸(quiet · small_space · no_props · focus_factor · with_companion)도 싣는다")
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
                .andExpect(jsonPath("$.profile_refs.length()").value(1))
                .andExpect(jsonPath("$.period.start_date").value("2026-09-09"))
                .andExpect(jsonPath("$.period.weeks").value(1))
                .andExpect(jsonPath("$.constraints.days_per_week").value(1))
                .andExpect(jsonPath("$.constraints.minutes_per_session").value(20))
                .andExpect(jsonPath("$.constraints.weekly_minutes").doesNotExist())
                .andExpect(jsonPath("$.constraints.quiet").value(true))
                .andExpect(jsonPath("$.constraints.small_space").value(true))
                .andExpect(jsonPath("$.constraints.no_props").value(true))
                .andExpect(jsonPath("$.constraints.focus_factor").value("민첩성"))
                .andExpect(jsonPath("$.constraints.with_companion").value(true))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"run_id\":\"cr_1\",\"status\":\"running\",\"poll_after_ms\":1500}"));

        CoachRunAccepted accepted = gateway.startCoachRun(dailyRequest());

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

        AiRunInProgressException e =
                assertThrows(AiRunInProgressException.class, () -> gateway.startCoachRun(dailyRequest()));

        assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
        assertThat(e.getMessage()).isEqualTo("already running");
    }

    @Test
    @DisplayName("getCoachRun 은 AI 명세의 편성 결과 예시(9/17 클립 형식)를 그대로 읽는다")
    void getCoachRun_은_AI_명세의_편성_결과_예시를_그대로_읽는다() {
        // ai:docs/인터페이스-명세.md 4장 응답 200 예시 그대로(extra_field 만 더했다).
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_01J7Q3M8VZ2K"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"run_id":"cr_01J7Q3M8VZ2K","status":"succeeded",
                         "steps":[{"seq":1,"name":"assess","status":"ok","summary":"유연성 백분위 24 · 대상 요인 = 유연성"},
                                  {"seq":2,"name":"retrieve","status":"ok","summary":"처방 청크 6건 · 클립 후보 488개"},
                                  {"seq":3,"name":"compose","status":"ok","summary":"미션 3건 · 클립 38개 · 코치가 편성"},
                                  {"seq":4,"name":"verify","status":"ok","summary":"인용 2건 · 금지 어휘 0건"}],
                         "proposal":{"missions":[{"kind":"일간","title":"월요일 늘이기",
                           "period":{"start_date":"2026-09-07","end_date":"2026-09-07"},
                           "participants":[{"ref":"p_c7a91f","role":"주행자"}],
                           "duration_min":15,"video_sec":418,
                           "sessions":[{"day_offset":0,"phase":"준비운동","order":1,
                                        "exercise_name":"넙다리 안쪽 늘리기 (나비자세)","fitness_factor":"유연성",
                                        "duration_sec":38,
                                        "video":{"video_id":"Eg3GpTv7z8s","start_sec":144,"end_sec":182},
                                        "evidence":[1,2]}],
                           "copy":{"child":"이번 주는 몸을 길게 늘이는 동작, 엄마랑 같이 해볼까요",
                                   "parent":"유연성은 지금 키우기 좋은 영역입니다. 주 3회 15분이면 충분합니다"},
                           "reason":"또래 처방에 나온 늘이는 동작을 앞세워 골랐습니다 [1]."}],
                          "citations":[{"index":1,"label":"국민체력100 운동처방 · 유소년 11세","chunk_id":"prescription:유소년-11-F-0142"}],
                          "notices":[]},
                         "refused":false,"refusal_reason":null,"extra_field":"ignored"}\
                        """, MediaType.APPLICATION_JSON));

        CoachRunResult result = gateway.getCoachRun("cr_01J7Q3M8VZ2K");

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.steps()).hasSize(4);
        assertThat(result.proposal().missions()).hasSize(1);
        CoachRunResult.Mission mission = result.proposal().missions().getFirst();
        assertThat(mission.kind()).isEqualTo("일간");
        assertThat(mission.startDate()).isEqualTo("2026-09-07");
        assertThat(mission.endDate()).isEqualTo("2026-09-07");
        assertThat(mission.participants())
                .singleElement()
                .isEqualTo(new CoachRunResult.ParticipantRef("p_c7a91f", "주행자"));
        assertThat(mission.durationMin()).isEqualTo(15);
        assertThat(mission.videoSec()).isEqualTo(418);
        assertThat(mission.reason()).isEqualTo("또래 처방에 나온 늘이는 동작을 앞세워 골랐습니다 [1].");
        assertThat(mission.copyParent()).isEqualTo("유연성은 지금 키우기 좋은 영역입니다. 주 3회 15분이면 충분합니다");
        assertThat(mission.sessions())
                .singleElement()
                .isEqualTo(new CoachRunResult.Session(
                        0,
                        "준비운동",
                        1,
                        "넙다리 안쪽 늘리기 (나비자세)",
                        "유연성",
                        38,
                        new CoachRunResult.Video("Eg3GpTv7z8s", 144, 182),
                        List.of(1, 2)));
        assertThat(result.proposal().citations())
                .singleElement()
                .isEqualTo(new Citation(1, "국민체력100 운동처방 · 유소년 11세", "prescription:유소년-11-F-0142", null));
        assertThat(result.proposal().notices()).isEmpty();
    }

    @Test
    @DisplayName("숫자 칸이 빠져도 응답을 읽는다 — notices 가 없으면 빈 목록, 옛 모양이면 세션 duration_min 합을 목표 분으로 쓴다")
    void 숫자_칸이_빠져도_응답을_읽는다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_2"))
                .andRespond(withSuccess("""
                        {"run_id":"cr_2","status":"succeeded","steps":[],
                         "proposal":{"missions":[
                            {"kind":"일간","title":"빠진 칸","period":{"start_date":"2026-09-07","end_date":"2026-09-07"},
                             "participants":[{"ref":"p_abc","role":"주행자"}],
                             "sessions":[{"phase":"본운동","exercise_name":"앞으로 숙이기","fitness_factor":"유연성",
                                          "video":{"video_id":"IdpXx2gm90o"},"evidence":[1]}],
                             "copy":{"child":"c","parent":"p"}},
                            {"title":"옛 모양","period":{"start_date":"2026-09-07","end_date":"2026-09-13"},
                             "participants":[{"ref":"p_abc","role":"주행자"}],
                             "sessions":[{"day_offset":0,"exercise_name":"앞으로 숙이기","fitness_factor":"유연성","duration_min":15,"evidence":[1]},
                                         {"day_offset":2,"exercise_name":"옆으로 숙이기","fitness_factor":"유연성","duration_min":15,"evidence":[]}],
                             "copy":{"child":"c","parent":"p"}}],
                          "citations":[{"index":1,"label":"처방","chunk_id":"prescription:1"}]},
                         "refused":false,"refusal_reason":null}\
                        """, MediaType.APPLICATION_JSON));

        CoachRunResult result = gateway.getCoachRun("cr_2");

        CoachRunResult.Mission missing = result.proposal().missions().getFirst();
        assertThat(missing.durationMin()).isNull();
        assertThat(missing.videoSec()).isNull();
        assertThat(missing.reason()).isEmpty();
        CoachRunResult.Session session = missing.sessions().getFirst();
        assertThat(session.dayOffset()).isNull();
        assertThat(session.order()).isNull();
        assertThat(session.durationSec()).isNull();
        assertThat(session.video()).isEqualTo(new CoachRunResult.Video("IdpXx2gm90o", null, null));

        CoachRunResult.Mission legacy = result.proposal().missions().getLast();
        assertThat(legacy.kind()).isEmpty();
        assertThat(legacy.durationMin()).isEqualTo(30);
        assertThat(legacy.sessions().getFirst().phase()).isEmpty();
        assertThat(result.proposal().notices()).isEmpty();
    }

    @Test
    @DisplayName("video 에 video_id 가 없거나 null · 빈칸이면 영상 없는 세션으로 읽는다 — 다른 칸은 그대로 읽는다")
    void video_id_가_없는_영상은_영상_없는_세션으로_읽는다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_3"))
                .andRespond(withSuccess("""
                        {"run_id":"cr_3","status":"succeeded","steps":[],
                         "proposal":{"missions":[
                            {"kind":"일간","title":"영상 id 빠짐","period":{"start_date":"2026-09-07","end_date":"2026-09-07"},
                             "participants":[{"ref":"p_abc","role":"주행자"}],"duration_min":10,
                             "sessions":[{"phase":"준비운동","order":1,"exercise_name":"목 돌리기","fitness_factor":"유연성",
                                          "video":{"start_sec":10,"end_sec":40},"evidence":[1]},
                                         {"phase":"본운동","order":2,"exercise_name":"앞으로 숙이기","fitness_factor":"유연성",
                                          "video":{"video_id":null,"start_sec":50,"end_sec":90},"evidence":[]},
                                         {"phase":"본운동","order":3,"exercise_name":"옆으로 숙이기","fitness_factor":"유연성",
                                          "video":{"video_id":"  ","start_sec":0},"evidence":[]},
                                         {"phase":"정리운동","order":4,"exercise_name":"나비자세","fitness_factor":"유연성",
                                          "video":{"video_id":"Eg3GpTv7z8s","start_sec":144,"end_sec":182},"evidence":[]}],
                             "copy":{"child":"c","parent":"p"}}],
                          "citations":[{"index":1,"label":"처방","chunk_id":"prescription:1"}]},
                         "refused":false,"refusal_reason":null}\
                        """, MediaType.APPLICATION_JSON));

        List<CoachRunResult.Session> sessions =
                gateway.getCoachRun("cr_3").proposal().missions().getFirst().sessions();

        assertThat(sessions).hasSize(4);
        assertThat(sessions.subList(0, 3))
                .allSatisfy(it -> assertThat(it.video()).isNull());
        assertThat(sessions.get(1).exerciseName()).isEqualTo("앞으로 숙이기");
        assertThat(sessions.getLast().video()).isEqualTo(new CoachRunResult.Video("Eg3GpTv7z8s", 144, 182));
    }

    @Test
    @DisplayName("video 의 source · media_url 을 읽는다 — 공단 영상은 kspo 와 mp4 주소, 유튜브는 youtube 와 null, 옛 응답은 둘 다 null")
    void video_의_source_와_media_url_을_읽는다() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_4"))
                .andRespond(withSuccess("""
                        {"run_id":"cr_4","status":"succeeded","steps":[],
                         "proposal":{"missions":[
                            {"kind":"일간","title":"공단 영상","period":{"start_date":"2026-09-07","end_date":"2026-09-07"},
                             "participants":[{"ref":"p_abc","role":"주행자"}],"duration_min":10,
                             "sessions":[{"phase":"본운동","order":1,"exercise_name":"팔굽혀펴기","fitness_factor":"근력",
                                          "video":{"video_id":"0AUDLJ08S_00351","start_sec":0,"end_sec":91,"source":"kspo",
                                                   "media_url":"https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00351.mp4"},
                                          "evidence":[1]},
                                         {"phase":"정리운동","order":2,"exercise_name":"나비자세","fitness_factor":"유연성",
                                          "video":{"video_id":"Eg3GpTv7z8s","start_sec":144,"end_sec":182,"source":"youtube"},
                                          "evidence":[]},
                                         {"phase":"정리운동","order":3,"exercise_name":"목 돌리기","fitness_factor":"유연성",
                                          "video":{"video_id":"IdpXx2gm90o","start_sec":56,"end_sec":96,"media_url":""},
                                          "evidence":[]}],
                             "copy":{"child":"c","parent":"p"}}],
                          "citations":[{"index":1,"label":"국민체력100 동영상 정보 · 팔굽혀펴기","chunk_id":"kspo:0AUDLJ08S_00351",
                                        "url":"https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00351.mp4"}]},
                         "refused":false,"refusal_reason":null}\
                        """, MediaType.APPLICATION_JSON));

        List<CoachRunResult.Session> sessions =
                gateway.getCoachRun("cr_4").proposal().missions().getFirst().sessions();

        assertThat(sessions)
                .extracting(CoachRunResult.Session::video)
                .containsExactly(
                        new CoachRunResult.Video(
                                "0AUDLJ08S_00351",
                                0,
                                91,
                                "kspo",
                                "https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00351.mp4"),
                        new CoachRunResult.Video("Eg3GpTv7z8s", 144, 182, "youtube", null),
                        new CoachRunResult.Video("IdpXx2gm90o", 56, 96));
        assertThat(sessions.getFirst().video().isKspo()).isTrue();
        assertThat(sessions.get(1).video().isKspo()).isFalse();
    }

    @Test
    @DisplayName("200 인데 JSON 이 깨졌으면 AiUnavailableException — 코치 실행이 대체 편성으로 넘어간다")
    void 깨진_JSON_은_AiUnavailableException() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs"))
                .andRespond(withSuccess("{\"run_id\":\"cr_1\",\"status\":", MediaType.APPLICATION_JSON));

        AiUnavailableException e =
                assertThrows(AiUnavailableException.class, () -> gateway.startCoachRun(dailyRequest()));

        assertThat(e.getCode()).isEqualTo("TEMPORARILY_UNAVAILABLE");
        assertThat(e.getMessage()).contains("POST /coach/runs");
    }

    @Test
    @DisplayName("프록시 오류 페이지처럼 text/html 이 오면 AiUnavailableException")
    void HTML_응답은_AiUnavailableException() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_1"))
                .andRespond(withSuccess("<html><body>502 Bad Gateway</body></html>", MediaType.TEXT_HTML));

        assertThrows(AiUnavailableException.class, () -> gateway.getCoachRun("cr_1"));
    }

    @Test
    @DisplayName("칸이 빠져 도메인으로 바꾸다 실패하면(period 없음 → NPE) AiUnavailableException")
    void 변환_실패는_AiUnavailableException() {
        server.expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_4"))
                .andRespond(withSuccess("""
                        {"run_id":"cr_4","status":"succeeded","steps":[],
                         "proposal":{"missions":[{"kind":"일간","title":"기간 빠짐",
                            "participants":[{"ref":"p_abc","role":"주행자"}],"sessions":[]}],"citations":[]},
                         "refused":false,"refusal_reason":null}\
                        """, MediaType.APPLICATION_JSON));

        AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> gateway.getCoachRun("cr_4"));

        assertThat(e.getMessage()).contains("GET coach/runs/cr_4");
    }

    @Test
    @DisplayName("assessment 는 해석 실패에도 재시도하고, 세 번 다 실패하면 AiUnavailableException")
    void assessment_는_해석_실패에도_재시도한다() {
        server.expect(ExpectedCount.times(3), requestTo("http://ai.internal:8000/v1/fitness/assessment"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThrows(AiUnavailableException.class, () -> gateway.assess(new AssessmentRequest(child)));
        server.verify();
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
    @DisplayName("400 은 재시도 없이 AI_BAD_REQUEST 이고 AI 가 준 코드를 문구에 싣는다")
    void 은_재시도_없이_AI_BAD_REQUEST_이고_AI_가_준_코드를_문구에_싣는다() {
        server.expect(ExpectedCount.once(), requestTo("http://ai.internal:8000/v1/fitness/assessment"))
                .andExpect(jsonPath("$.profile_ref").value(child.profileRef()))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"ITEM_NOT_ALLOWED\",\"message\":\"005\"}}"));

        AiBadRequestException e =
                assertThrows(AiBadRequestException.class, () -> gateway.assess(new AssessmentRequest(child)));

        assertThat(e.getCode()).isEqualTo("AI_BAD_REQUEST");
        assertThat(e.getMessage()).contains("ITEM_NOT_ALLOWED");
        server.verify();
    }
}
