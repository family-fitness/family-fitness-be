package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType
import kr.ac.kookmin.familyfitness.coaching.support.FakeAiGateway
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.Fixed
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.support.Videos
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunAccepted
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class CoachRunExecutorTest {
    private val family = Family()
    private val identity = FakeIdentity(family)
    private val fitness = FakeFitness().apply { measured(family.child.profileId, "012" to 8.0, "028" to 45.0) }
    private val runs = InMemoryCoachRunRepository()
    private val gateway = FakeAiGateway()
    private val pipeline =
        CoachRunPipeline(
            runs,
            identity,
            fitness,
            InMemoryExerciseVideoRepository(Videos.seed()),
            JsonMapper.builder().build(),
            Fixed.time(),
        )
    private val executor = CoachRunExecutor(pipeline, gateway, pollIntervalMs = 0, maxPolls = 3)

    private fun runningRun(): CoachRun =
        runs.save(
            CoachRun.start(
                id = UUID.randomUUID(),
                familyId = family.familyId,
                weekStart = Fixed.WEEK_START,
                triggerType = TriggerType.MANUAL,
                daysPerWeek = 3,
                minutesPerSession = 15,
                requestedBy = family.parent.profileId,
                at = Fixed.NOW,
            ),
        )

    @Test
    fun `succeeded 면 제안이 변환되어 AWAITING_APPROVAL 이 되고 AI 요청에는 이름 없이 역할만 실린다`() {
        val run = runningRun()

        executor.execute(run.id)

        val saved = runs.findById(run.id)!!
        assertThat(saved.status).isEqualTo(CoachRunStatus.AWAITING_APPROVAL)
        assertThat(saved.aiRunId).startsWith("cr_")
        assertThat(saved.steps.map { it.name }).containsExactly("assess", "retrieve", "compose", "verify")
        assertThat(saved.summary).contains("유연성")
        assertThat(saved.proposalJson).contains("같이 늘이는 한 주")
        val item = saved.proposals.single()
        assertThat(item.targetMetric).isEqualTo("TIMER_MINUTES")
        assertThat(item.targetValue).isEqualTo(45)
        assertThat(item.video!!.videoId).isEqualTo("IdpXx2gm90o")
        assertThat(item.participants.map { it.profileId }).containsExactlyInAnyOrder(family.child.profileId, family.parent.profileId)
        assertThat(item.participants.map { it.coachRole }).containsExactlyInAnyOrder("주행자", "동반자")
        assertThat(item.citations.map { it.index }).containsExactly(1, 2)

        val request = gateway.startRequests.single()
        assertThat(request.startDate).isEqualTo("2026-09-07")
        assertThat(request.profiles.map { it.role }).containsExactlyInAnyOrder("동반자", "주행자", "응원")
        val child = request.profiles.first { it.profile.profileRef == ProfileRef.of(family.child.profileId) }
        assertThat(child.profile.age).isEqualTo(11)
        assertThat(child.profile.measurements).containsKeys("012", "028")
        assertThat(child.profile.inputLevel).isEqualTo("L2")
        assertThat(request.toString()).doesNotContain("민준")
    }

    @Test
    fun `refused 면 FAILED 이고 사유는 refused 접두어로 남는다`() {
        val run = runningRun()
        gateway.onPoll =
            { id ->
                CoachRunResult(id, "refused", listOf(CoachRunResult.Step(1, "assess", "refused", "연령 필터")), null, true, "age_filter_empty")
            }

        executor.execute(run.id)

        val saved = runs.findById(run.id)!!
        assertThat(saved.status).isEqualTo(CoachRunStatus.FAILED)
        assertThat(saved.failureReason).isEqualTo("refused: age_filter_empty")
        assertThat(saved.aiRefused).isTrue()
        assertThat(saved.aiRefusalReason).isEqualTo("age_filter_empty")
        assertThat(saved.steps.single().status).isEqualTo("refused")
        assertThat(saved.proposals).isEmpty()
    }

    @Test
    fun `running 이 계속되면 최대 횟수까지 폴링하고 timeout 으로 FAILED`() {
        val run = runningRun()
        gateway.onPoll = { id -> CoachRunResult(id, "running", emptyList(), null, false, null) }

        executor.execute(run.id)

        assertThat(gateway.pollCount).isEqualTo(3)
        val saved = runs.findById(run.id)!!
        assertThat(saved.status).isEqualTo(CoachRunStatus.FAILED)
        assertThat(saved.failureReason).startsWith("timeout")
    }

    @Test
    fun `게이트웨이 예외는 FAILED 로 끝나고 상태 전이는 한 번만 일어난다`() {
        val run = runningRun()
        gateway.onStart = { throw AiUnavailableException("연결 실패") }

        executor.execute(run.id)

        val saved = runs.findById(run.id)!!
        assertThat(saved.status).isEqualTo(CoachRunStatus.FAILED)
        assertThat(saved.failureReason).contains("AiUnavailableException")

        executor.execute(run.id)
        assertThat(runs.findById(run.id)!!.status).isEqualTo(CoachRunStatus.FAILED)
    }

    @Test
    fun `AI 가 failed 를 돌려주면 FAILED`() {
        val run = runningRun()
        gateway.onStart = { CoachRunAccepted("cr_x", "running", 1000) }
        gateway.onPoll = { id -> CoachRunResult(id, "failed", emptyList(), null, false, null) }

        executor.execute(run.id)

        assertThat(runs.findById(run.id)!!.failureReason).isEqualTo("failed: AI run status=failed")
    }
}
