package kr.ac.kookmin.familyfitness.shared.ai

import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration

/** 실제 HTTP 없이 [MockRestServiceServer] 로 와이어 형식과 오류 매핑을 본다. */
class HttpAiGatewayTest {
    private val builder: RestClient.Builder = RestClient.builder()
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    private val gateway =
        HttpAiGateway(
            builder,
            AppProperties(ai = AppProperties.Ai(mode = "http", baseUrl = "http://ai.internal:8000/")),
            factoryFor = { null },
            retryBackoff = Duration.ZERO,
        )
    private val child = AiProfile("p_abc", 11, "세", "M", 140.5, 35.0, mapOf("012" to 8.0))

    @Test
    fun `startCoachRun 은 snake_case 본문을 보내고 202 접수를 매핑한다`() {
        server
            .expect(requestTo("http://ai.internal:8000/v1/coach/runs"))
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
            .andRespond(
                withStatus(HttpStatus.ACCEPTED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"run_id":"cr_1","status":"running","poll_after_ms":1500}"""),
            )

        val accepted =
            gateway.startCoachRun(
                CoachRunRequest(listOf(CoachRunRequest.Participant(child, "주행자")), "2026-09-07", 1, 3, 15),
            )

        assertThat(accepted).isEqualTo(CoachRunAccepted("cr_1", "running", 1500))
        server.verify()
    }

    @Test
    fun `409 오류 봉투는 RUN_IN_PROGRESS 예외가 된다`() {
        server
            .expect(requestTo("http://ai.internal:8000/v1/coach/runs"))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"RUN_IN_PROGRESS","message":"already running"}}"""),
            )

        val e =
            assertThrows<AiRunInProgressException> {
                gateway.startCoachRun(CoachRunRequest(listOf(CoachRunRequest.Participant(child, "주행자")), "2026-09-07", 1, 3, 15))
            }

        assertThat(e.code).isEqualTo("RUN_IN_PROGRESS")
        assertThat(e.message).isEqualTo("already running")
    }

    @Test
    fun `getCoachRun 은 succeeded proposal 을 도메인 DTO 로 매핑한다`() {
        server
            .expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {"run_id":"cr_1","status":"succeeded",
                     "steps":[{"seq":1,"name":"assess","status":"ok","summary":"측정 1명"}],
                     "proposal":{"missions":[{"title":"같이 늘이는 한 주","period":{"start_date":"2026-09-07","end_date":"2026-09-13"},
                       "participants":[{"ref":"p_abc","role":"주행자"}],
                       "sessions":[{"day_offset":0,"exercise_name":"앞으로 숙이기","fitness_factor":"유연성","duration_min":15,
                                    "video":{"video_id":"IdpXx2gm90o","start_sec":96},"evidence":[1]},
                                   {"day_offset":2,"exercise_name":"옆으로 숙이기","fitness_factor":"유연성","duration_min":15,"video":null,"evidence":[]}],
                       "copy":{"child":"해보자","parent":"부모 문구"}}],
                      "citations":[{"index":1,"label":"처방","chunk_id":"prescription:1"}]},
                     "refused":false,"refusal_reason":null,"extra_field":"ignored"}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.getCoachRun("cr_1")

        assertThat(result.status).isEqualTo("succeeded")
        assertThat(result.steps.single()).isEqualTo(CoachRunResult.Step(1, "assess", "ok", "측정 1명"))
        val mission = result.proposal!!.missions.single()
        assertThat(mission.startDate).isEqualTo("2026-09-07")
        assertThat(mission.participants.single()).isEqualTo(CoachRunResult.ParticipantRef("p_abc", "주행자"))
        assertThat(mission.sessions.first().video).isEqualTo(CoachRunResult.Video("IdpXx2gm90o", 96))
        assertThat(mission.sessions.last().video).isNull()
        assertThat(mission.sessions.sumOf { it.durationMin }).isEqualTo(30)
        assertThat(mission.copyParent).isEqualTo("부모 문구")
        assertThat(result.proposal!!.citations.single()).isEqualTo(Citation(1, "처방", "prescription:1", null))
    }

    @Test
    fun `404 는 RUN_NOT_FOUND 다`() {
        server
            .expect(requestTo("http://ai.internal:8000/v1/coach/runs/cr_x"))
            .andRespond(
                withStatus(
                    HttpStatus.NOT_FOUND,
                ).contentType(MediaType.APPLICATION_JSON).body("""{"error":{"code":"RUN_NOT_FOUND","message":"no"}}"""),
            )

        assertThat(assertThrows<AiRunNotFoundException> { gateway.getCoachRun("cr_x") }.code).isEqualTo("RUN_NOT_FOUND")
    }

    @Test
    fun `ask 는 profile_ref·age_group·question 을 보내고 답·인용·거부를 매핑한다`() {
        server
            .expect(requestTo("http://ai.internal:8000/v1/coach/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.profile_ref").value("p_abc"))
            .andExpect(jsonPath("$.age_group").value("유소년"))
            .andExpect(jsonPath("$.question").value("질문"))
            .andRespond(
                withSuccess(
                    """{"answer":"답 [1]","citations":[{"index":1,"label":"처방","chunk_id":"c1","url":"https://x"}],"refused":false,"refusal_reason":null}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = gateway.ask(CoachMessageRequest("p_abc", "유소년", "질문"))

        assertThat(response.answer).isEqualTo("답 [1]")
        assertThat(response.citations.single()).isEqualTo(Citation(1, "처방", "c1", "https://x"))
        assertThat(response.refused).isFalse()
    }

    @Test
    fun `503 은 AiUnavailableException 이고 messages 는 재시도하지 않는다`() {
        server
            .expect(ExpectedCount.once(), requestTo("http://ai.internal:8000/v1/coach/messages"))
            .andRespond(
                withStatus(
                    HttpStatus.SERVICE_UNAVAILABLE,
                ).contentType(MediaType.APPLICATION_JSON).body("""{"error":{"code":"TEMPORARILY_UNAVAILABLE","message":"llm down"}}"""),
            )

        val e = assertThrows<AiUnavailableException> { gateway.ask(CoachMessageRequest("p_abc", "유소년", "질문")) }

        assertThat(e.code).isEqualTo("TEMPORARILY_UNAVAILABLE")
        assertThat(e.message).contains("llm down")
        server.verify()
    }

    @Test
    fun `assessment 는 503 에 두 번 재시도하고 세 번째 성공을 돌려준다`() {
        server
            .expect(ExpectedCount.times(2), requestTo("http://ai.internal:8000/v1/fitness/assessment"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        server
            .expect(ExpectedCount.once(), requestTo("http://ai.internal:8000/v1/fitness/assessment"))
            .andExpect(jsonPath("$.profile_ref").value("p_abc"))
            .andExpect(jsonPath("$.age_unit").value("세"))
            .andRespond(
                withSuccess(
                    """{"input_level":"L2","age_group":"유소년","child_scope":{"focus_one":{"factor":"유연성","copy":"키우기 좋은 영역"}},
                        "parent_scope":{"grade":"3등급","peer_distribution":[{"grade":"1등급","ratio":0.1}],
                        "factors":[{"factor":"유연성","item_code":"012","item_name":"앉아윗몸앞으로굽히기","item_label":"앉아윗몸앞으로굽히기","unit":"cm","value":8.0,"score":50.0,"percentile":48,"band":"steady","n":120}],
                        "copy":{"strength":"a","focus":"b"}},"low_sample":false,"disclaimer":"참고"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = gateway.assess(AssessmentRequest(child))

        assertThat(response.inputLevel).isEqualTo("L2")
        assertThat(response.childScope.focusOne!!.factor).isEqualTo("유연성")
        assertThat(response.parentScope.grade).isEqualTo("3등급")
        assertThat(
            response.parentScope.factors
                .single()
                .percentile,
        ).isEqualTo(48)
        assertThat(response.parentScope.copy).containsEntry("focus", "b")
        server.verify()
    }

    @Test
    fun `400 은 재시도 없이 AI_BAD_REQUEST 이고 trajectory 는 item_code·horizon_years 를 보낸다`() {
        server
            .expect(ExpectedCount.once(), requestTo("http://ai.internal:8000/v1/fitness/trajectory"))
            .andExpect(jsonPath("$.item_code").value("028"))
            .andExpect(jsonPath("$.horizon_years").value(10))
            .andRespond(
                withStatus(
                    HttpStatus.BAD_REQUEST,
                ).contentType(MediaType.APPLICATION_JSON).body("""{"error":{"code":"ITEM_NOT_ALLOWED","message":"005"}}"""),
            )

        val e = assertThrows<AiBadRequestException> { gateway.trajectory(TrajectoryRequest(child)) }

        assertThat(e.code).isEqualTo("AI_BAD_REQUEST")
        assertThat(e.message).contains("ITEM_NOT_ALLOWED")
        server.verify()
    }
}
