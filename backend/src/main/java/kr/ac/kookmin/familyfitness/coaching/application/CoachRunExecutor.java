package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunAccepted;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 코치 실행 비동기 파이프라인. 요청 트랜잭션이 커밋된 뒤 실행된다.
 * startCoachRun → getCoachRun 을 {@code pollIntervalMs} 간격으로 최대 {@code maxPolls} 회 → 변환·저장.
 * 어떤 예외든 FAILED 로 끝내고 로그를 남긴다. 사용자는 GET /coach/runs/{id} 로 결과를 본다.
 */
@Component
public class CoachRunExecutor {
    /** 계약: 1.5s 간격 · 최대 40회. */
    public static final long DEFAULT_POLL_INTERVAL_MS = 1500L;

    public static final int DEFAULT_MAX_POLLS = 40;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoachRunPipeline pipeline;
    private final AiGateway gateway;
    private final long pollIntervalMs;
    private final int maxPolls;

    public CoachRunExecutor(
            CoachRunPipeline pipeline,
            AiGateway gateway,
            @Value("${app.coach.poll-interval-ms:1500}") long pollIntervalMs,
            @Value("${app.coach.max-polls:40}") int maxPolls) {
        this.pipeline = pipeline;
        this.gateway = gateway;
        this.pollIntervalMs = pollIntervalMs;
        this.maxPolls = maxPolls;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CoachRunRequested event) {
        execute(event.runId());
    }

    public void execute(UUID runId) {
        try {
            CoachRunRequest request = pipeline.prepare(runId);
            CoachRunAccepted accepted = gateway.startCoachRun(request);
            pipeline.attachAiRun(runId, accepted.runId());
            CoachRunResult result = poll(accepted.runId());
            if (result == null) {
                pipeline.fail(
                        runId,
                        "timeout: AI run " + accepted.runId() + " 이 " + maxPolls + "회 폴링 안에 끝나지 않았다",
                        null,
                        false,
                        null);
            } else if (result.refused() || result.status().equals("refused")) {
                String refusalReason = result.refusalReason();
                pipeline.fail(
                        runId,
                        "refused: " + (refusalReason == null ? "unknown" : refusalReason),
                        steps(result),
                        true,
                        refusalReason);
            } else if (result.status().equals("succeeded")) {
                pipeline.complete(runId, result);
            } else {
                pipeline.fail(runId, "failed: AI run status=" + result.status(), steps(result), false, null);
            }
        } catch (AiUnavailableException e) {
            log.warn("AI 서비스 장애 → 라벨 기반 대체 편성: run={} ({})", runId, e.getMessage());
            try {
                fallbackOrFail(runId, e.getMessage() == null ? "unavailable" : e.getMessage());
            } catch (RuntimeException failure) {
                log.error("대체 편성도 실패: run={}", runId, failure);
            }
        } catch (Exception e) {
            log.error("코치 실행 실패: run={}", runId, e);
            try {
                pipeline.fail(runId, e.getClass().getSimpleName() + ": " + e.getMessage(), null, false, null);
            } catch (RuntimeException failure) {
                log.error("실패 기록도 실패: run={}", runId, failure);
            }
        }
    }

    /** 보드 F3 「LLM 없이도 돈다」 — 라벨만으로 편성. 근거를 만들 측정이 없으면 FAILED. */
    private void fallbackOrFail(UUID runId, String reason) {
        CoachRunResult result = pipeline.planFallback(runId, reason);
        if (result == null) {
            pipeline.fail(runId, "AI 장애(" + reason + ") 이고 대체 편성 근거(측정)도 없다", null, false, null);
        } else {
            pipeline.complete(runId, result);
        }
    }

    private @Nullable CoachRunResult poll(String aiRunId) throws InterruptedException {
        for (int attempt = 0; attempt < maxPolls; attempt++) {
            if (attempt > 0 && pollIntervalMs > 0) Thread.sleep(pollIntervalMs);
            CoachRunResult result = gateway.getCoachRun(aiRunId);
            if (!result.isRunning()) return result;
        }
        return null;
    }

    private static List<CoachStep> steps(CoachRunResult result) {
        return ProposalConverter.steps(result);
    }
}
