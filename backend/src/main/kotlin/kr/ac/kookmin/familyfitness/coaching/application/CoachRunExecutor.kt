package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * 파이프라인의 트랜잭션 단계. 폴링(최대 60초) 동안 트랜잭션을 잡지 않도록 준비·마무리를 따로 자른다.
 * 비동기 리스너가 AFTER_COMMIT 콜백 안에서 부르므로 항상 REQUIRES_NEW.
 */
@Component
class CoachRunPipeline(
    private val runs: CoachRunRepository,
    private val profileQuery: ProfileQuery,
    private val fitnessQuery: FitnessQuery,
    private val videos: ExerciseVideoRepository,
    private val jsonMapper: JsonMapper,
    private val time: AppTime,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 가족 프로필과 최신 측정으로 AI 요청을 만든다. AI 로 이름·생년월일은 나가지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun prepare(runId: UUID): CoachRunRequest {
        val run = runs.findById(runId) ?: throw CoachRunNotFoundException(runId)
        val today = time.today()
        val participants =
            profileQuery.detailsOfFamily(run.familyId).map { details ->
                val latest = fitnessQuery.latestOf(details.profileId)
                val profile = AiProfileFactory.of(details, latest?.measurements ?: emptyMap(), latest?.heightCm, latest?.weightKg, today)
                AiProfileFactory.participant(details, profile)
            }
        return CoachRunRequest(
            profiles = participants,
            startDate = run.weekStart.toString(),
            weeks = 1,
            daysPerWeek = run.daysPerWeek,
            minutesPerSession = run.minutesPerSession,
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun attachAiRun(
        runId: UUID,
        aiRunId: String,
    ) {
        val run = runs.findById(runId) ?: throw CoachRunNotFoundException(runId)
        run.attachAiRun(aiRunId, time.now())
        runs.save(run)
    }

    /** `succeeded` 결과를 제안 항목으로 바꿔 붙이고 AWAITING_APPROVAL 로 옮긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun complete(
        runId: UUID,
        result: CoachRunResult,
    ) {
        val run = runs.findById(runId) ?: throw CoachRunNotFoundException(runId)
        val proposal = result.proposal ?: throw IllegalStateException("succeeded 인데 proposal 이 없다: ${result.runId}")
        val members = profileQuery.detailsOfFamily(run.familyId)
        val converter =
            ProposalConverter(
                refIndex = ProfileRef.indexOf(members.map { it.profileId }),
                roles = members.associate { it.profileId to it.role },
                coachRoles = members.associate { it.profileId to CoachRoles.of(it.role, it.supportMode) },
                knownVideoIds =
                    videos
                        .findAllByIds(proposal.missions.flatMap { m -> m.sessions.mapNotNull { it.video?.videoId } }.toSet())
                        .map { it.videoId }
                        .toSet(),
            )
        run.complete(
            steps = ProposalConverter.steps(result),
            proposals = converter.convert(proposal),
            proposalJson = jsonMapper.writeValueAsString(proposal),
            summary = ProposalConverter.summary(result),
            modelName = null,
            at = time.now(),
        )
        runs.save(run)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun fail(
        runId: UUID,
        reason: String,
        steps: List<CoachStep>?,
        refused: Boolean,
        refusalReason: String?,
    ) {
        val run = runs.findById(runId) ?: throw CoachRunNotFoundException(runId)
        if (run.status != CoachRunStatus.RUNNING) {
            log.warn("이미 끝난 실행의 실패 기록은 무시한다: run={} status={}", runId, run.status)
            return
        }
        run.fail(reason, time.now(), steps ?: run.steps, refused, refusalReason)
        runs.save(run)
    }
}

/**
 * 코치 실행 비동기 파이프라인. 요청 트랜잭션이 커밋된 뒤 실행된다.
 * startCoachRun → getCoachRun 을 [pollIntervalMs] 간격으로 최대 [maxPolls] 회 → 변환·저장.
 * 어떤 예외든 FAILED 로 끝내고 로그를 남긴다. 사용자는 GET /coach/runs/{id} 로 결과를 본다.
 */
@Component
class CoachRunExecutor(
    private val pipeline: CoachRunPipeline,
    private val gateway: AiGateway,
    @Value("\${app.coach.poll-interval-ms:1500}") private val pollIntervalMs: Long,
    @Value("\${app.coach.max-polls:40}") private val maxPolls: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: CoachRunRequested) = execute(event.runId)

    fun execute(runId: UUID) {
        try {
            val request = pipeline.prepare(runId)
            val accepted = gateway.startCoachRun(request)
            pipeline.attachAiRun(runId, accepted.runId)
            val result = poll(accepted.runId)
            when {
                result == null -> {
                    pipeline.fail(runId, "timeout: AI run ${accepted.runId} 이 ${maxPolls}회 폴링 안에 끝나지 않았다", null, false, null)
                }

                result.refused || result.status == "refused" -> {
                    pipeline.fail(runId, "refused: ${result.refusalReason ?: "unknown"}", steps(result), true, result.refusalReason)
                }

                result.status == "succeeded" -> {
                    pipeline.complete(runId, result)
                }

                else -> {
                    pipeline.fail(runId, "failed: AI run status=${result.status}", steps(result), false, null)
                }
            }
        } catch (e: Exception) {
            log.error("코치 실행 실패: run={}", runId, e)
            runCatching { pipeline.fail(runId, "${e.javaClass.simpleName}: ${e.message}", null, false, null) }
                .onFailure { log.error("실패 기록도 실패: run={}", runId, it) }
        }
    }

    private fun poll(aiRunId: String): CoachRunResult? {
        repeat(maxPolls) { attempt ->
            if (attempt > 0 && pollIntervalMs > 0) Thread.sleep(pollIntervalMs)
            val result = gateway.getCoachRun(aiRunId)
            if (!result.isRunning) return result
        }
        return null
    }

    private fun steps(result: CoachRunResult) = ProposalConverter.steps(result)

    companion object {
        /** 계약: 1.5s 간격 · 최대 40회. */
        const val DEFAULT_POLL_INTERVAL_MS = 1500L
        const val DEFAULT_MAX_POLLS = 40
    }
}
