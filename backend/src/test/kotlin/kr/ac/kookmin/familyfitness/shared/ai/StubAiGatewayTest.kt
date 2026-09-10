package kr.ac.kookmin.familyfitness.shared.ai

import kr.ac.kookmin.familyfitness.shared.domain.Copy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StubAiGatewayTest {
    private val gateway = StubAiGateway()
    private val child = AiProfile("p_child", 11, "세", "M", 140.0, 35.0, mapOf("012" to 8.0))
    private val parent = AiProfile("p_parent", 41, "세", "F", null, null, emptyMap())
    private val cheer = AiProfile("p_cheer", 43, "세", "M", null, null, emptyMap())

    private fun request(vararg participants: CoachRunRequest.Participant) =
        CoachRunRequest(participants.toList(), "2026-09-07", 1, daysPerWeek = 3, minutesPerSession = 15)

    @Test
    fun `startCoachRun 은 cr_ 식별자를 접수하고 getCoachRun 은 주행자마다 유연성 미션을 낸다`() {
        val accepted = gateway.startCoachRun(request(Participant(child, "주행자"), Participant(parent, "동반자"), Participant(cheer, "응원")))
        assertThat(accepted.runId).startsWith("cr_")
        assertThat(accepted.status).isEqualTo("running")

        val result = gateway.getCoachRun(accepted.runId)

        assertThat(result.status).isEqualTo("succeeded")
        assertThat(result.isRunning).isFalse()
        assertThat(result.refused).isFalse()
        assertThat(result.steps.map { it.name }).containsExactly("assess", "retrieve", "compose", "verify")
        assertThat(result.steps.all { it.status == "ok" }).isTrue()
        val mission = result.proposal!!.missions.single()
        assertThat(mission.title).isEqualTo("같이 늘이는 한 주")
        assertThat(mission.startDate).isEqualTo("2026-09-07")
        assertThat(mission.endDate).isEqualTo("2026-09-13")
        assertThat(mission.participants.map { it.ref }).containsExactly("p_child", "p_parent")
        assertThat(mission.sessions.map { it.dayOffset }).containsExactly(0, 2, 4)
        assertThat(mission.sessions.map { it.durationMin }).containsOnly(15)
        assertThat(mission.sessions.first().video).isEqualTo(CoachRunResult.Video("IdpXx2gm90o", 96))
        assertThat(mission.sessions[1].video).isNull()
        assertThat(mission.sessions.last().video).isEqualTo(CoachRunResult.Video("IdpXx2gm90o", 96))
        assertThat(mission.sessions.first().evidence).containsExactly(1, 2)
        assertThat(mission.copyParent).isNotBlank()
        assertThat(result.proposal!!.citations.map { it.chunkId }).containsExactly("prescription:유소년-11-F-0142", "video:IdpXx2gm90o")
    }

    @Test
    fun `주행자도 동반자도 없으면 refused`() {
        val accepted = gateway.startCoachRun(request(Participant(cheer, "응원")))

        val result = gateway.getCoachRun(accepted.runId)

        assertThat(result.status).isEqualTo("refused")
        assertThat(result.refused).isTrue()
        assertThat(result.refusalReason).isEqualTo("no_relevant_source")
        assertThat(result.proposal).isNull()
    }

    @Test
    fun `모르는 run 은 RUN_NOT_FOUND`() {
        assertThat(assertThrows<AiRunNotFoundException> { gateway.getCoachRun("cr_nope") }.code).isEqualTo("RUN_NOT_FOUND")
    }

    @Test
    fun `ask 는 인용 있는 답을 주고 의료 질문은 medical_query 로 거부한다`() {
        val ok = gateway.ask(CoachMessageRequest("p_child", "유소년", "유연성에 좋은 준비운동은?"))
        assertThat(ok.refused).isFalse()
        assertThat(ok.answer).endsWith("[1].")
        assertThat(ok.citations.single().index).isEqualTo(1)

        val refused = gateway.ask(CoachMessageRequest("p_child", "유소년", "무릎 부상 후 운동해도 되나요?"))
        assertThat(refused.refused).isTrue()
        assertThat(refused.refusalReason).isEqualTo("medical_query")
        assertThat(refused.citations).isEmpty()
    }

    @Test
    fun `trajectory 는 현재·+4·+8·+10 세 구간을 고정 notice 와 함께 돌려준다`() {
        val response = gateway.trajectory(TrajectoryRequest(child.copy(measurements = mapOf("028" to 40.0)), "028", 10))

        assertThat(response.basis).isEqualTo("cross_sectional_group_distribution")
        assertThat(response.itemCode).isEqualTo("028")
        assertThat(response.notice).isEqualTo(Copy.TRAJECTORY_NOTICE)
        assertThat(response.bands.map { it.age }).containsExactly(11, 15, 19, 21)
        assertThat(response.bands.first().p50).isEqualTo(40.0)
        assertThat(response.bands.all { it.p10!! < it.p50!! && it.p50!! < it.p90!! }).isTrue()
        assertThat(gateway.trajectory(TrajectoryRequest(parent, "028", 5)).bands.map { it.age }).containsExactly(41, 45)
    }

    @Test
    fun `assess 와 searchVideos 는 계약 모양의 단순 응답을 돌려준다`() {
        val assessment = gateway.assess(AssessmentRequest(child))
        assertThat(assessment.inputLevel).isEqualTo("L2")
        assertThat(assessment.ageGroup).isEqualTo("유소년")
        assertThat(assessment.disclaimer).isEqualTo(Copy.FITNESS_DISCLAIMER)
        assertThat(
            assessment.parentScope.factors
                .single()
                .itemCode,
        ).isEqualTo("012")

        val search = gateway.searchVideos(VideoSearchRequest("유소년", listOf("유연성")))
        assertThat(search.hits.single().videoId).isEqualTo("IdpXx2gm90o")
        assertThat(
            search.hits
                .single()
                .citation.index,
        ).isEqualTo(2)
    }
}

private typealias Participant = CoachRunRequest.Participant
