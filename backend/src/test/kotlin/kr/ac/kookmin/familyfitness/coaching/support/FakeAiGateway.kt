package kr.ac.kookmin.familyfitness.coaching.support

import kr.ac.kookmin.familyfitness.shared.ai.AiGateway
import kr.ac.kookmin.familyfitness.shared.ai.AssessmentRequest
import kr.ac.kookmin.familyfitness.shared.ai.AssessmentResponse
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageRequest
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageResponse
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunAccepted
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult
import kr.ac.kookmin.familyfitness.shared.ai.StubAiGateway
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryRequest
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryResponse
import kr.ac.kookmin.familyfitness.shared.ai.VideoSearchRequest
import kr.ac.kookmin.familyfitness.shared.ai.VideoSearchResponse

/** 스크립트 가능한 게이트웨이. 기본은 [StubAiGateway] 에 위임하고, 필요한 응답만 바꿔 끼운다. */
class FakeAiGateway(
    private val delegate: AiGateway = StubAiGateway(),
) : AiGateway {
    var onStart: ((CoachRunRequest) -> CoachRunAccepted)? = null
    var onPoll: ((String) -> CoachRunResult)? = null
    var onAsk: ((CoachMessageRequest) -> CoachMessageResponse)? = null
    val startRequests = mutableListOf<CoachRunRequest>()
    var pollCount = 0

    override fun assess(request: AssessmentRequest): AssessmentResponse = delegate.assess(request)

    override fun trajectory(request: TrajectoryRequest): TrajectoryResponse = delegate.trajectory(request)

    override fun searchVideos(request: VideoSearchRequest): VideoSearchResponse = delegate.searchVideos(request)

    override fun startCoachRun(request: CoachRunRequest): CoachRunAccepted {
        startRequests += request
        return onStart?.invoke(request) ?: delegate.startCoachRun(request)
    }

    override fun getCoachRun(runId: String): CoachRunResult {
        pollCount++
        return onPoll?.invoke(runId) ?: delegate.getCoachRun(runId)
    }

    override fun ask(request: CoachMessageRequest): CoachMessageResponse = onAsk?.invoke(request) ?: delegate.ask(request)
}
