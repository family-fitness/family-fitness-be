package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunFailureCode;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.AiRunNotFoundException;
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunAccepted;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 코치 실행 비동기 파이프라인. 요청 트랜잭션이 커밋된 뒤 편성 전용 스레드 풀({@link CoachRunExecutorConfig})에서 돈다.
 * startCoachRun → getCoachRun 을 {@code pollIntervalMs} 간격으로 최대 {@code maxPolls} 회 → 변환 · 저장.
 * 끝내는 차례(결정 17):
 * <ol>
 *   <li>AI 가 succeeded(제안 있음) → AWAITING_APPROVAL.
 *   <li>AI 가 refused → FAILED(NO_CITATIONS). 근거가 없다는 AI 의 판단이라 대체 편성도 하지 않는다.
 *   <li>AI 에 닿지 못함(연결 실패 · 5xx) · AI 가 failed · 폴링 만료 · 폴링 404(실행이 사라짐) → 라벨 기반 대체 편성.
 *       대체 편성할 근거(측정 · 보호자가 키워 주고 싶은 역량)가 없거나 대체 편성이 실패하면 FAILED(AI_FAILED).
 *   <li>폴링 한 번의 일시 오류(타임아웃 · 연결 실패 · 5xx)는 그 회차만 건너뛰고 다음 폴링으로 넘긴다.
 *   <li>보호자 동의가 그 사이 거둬졌으면 FAILED(CONSENT_REQUIRED), 그 밖의 예외(AI 400 등)는 FAILED(ERROR).
 * </ol>
 * 사용자는 GET /coach/runs/{id} 로 결과를 본다.
 */
@Component
public class CoachRunExecutor {
    /** 계약: 1.5s 간격 · 최대 40회. */
    public static final long DEFAULT_POLL_INTERVAL_MS = 1500L;

    public static final int DEFAULT_MAX_POLLS = 40;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoachRunPipeline pipeline;
    private final AiGateway gateway;
    private final TaskExecutor threads;
    private final long pollIntervalMs;
    private final int maxPolls;

    public CoachRunExecutor(
            CoachRunPipeline pipeline,
            AiGateway gateway,
            @Qualifier(CoachRunExecutorConfig.EXECUTOR) TaskExecutor threads,
            @Value("${app.coach.poll-interval-ms:1500}") long pollIntervalMs,
            @Value("${app.coach.max-polls:40}") int maxPolls) {
        this.pipeline = pipeline;
        this.gateway = gateway;
        this.threads = threads;
        this.pollIntervalMs = pollIntervalMs;
        this.maxPolls = maxPolls;
    }

    /**
     * 커밋 뒤 편성 스레드 풀에 넘긴다. 스레드와 대기열이 가득 차(또는 서버가 내려가는 중이라) 받지 못하면
     * 곧바로 FAILED(BUSY) — 정리 작업이 돌 때까지 RUNNING 으로 두어 (프로필, 날짜)를 잠그지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CoachRunRequested event) {
        UUID runId = event.runId();
        try {
            threads.execute(() -> execute(runId));
        } catch (TaskRejectedException e) {
            log.warn("편성 스레드와 대기열이 가득 차 시작하지 못했다: run={} ({})", runId, e.getMessage());
            failQuietly(runId, CoachRunFailureCode.BUSY, "busy: 편성 스레드와 대기열이 가득 차 받지 못했다");
        }
    }

    public void execute(UUID runId) {
        try {
            CoachRunRequest request = pipeline.prepare(runId);
            if (request == null) return; // 기다리는 사이 정리 작업이 끝낸 실행
            CoachRunAccepted accepted = gateway.startCoachRun(request);
            if (!pipeline.attachAiRun(runId, accepted.runId())) return; // 그 사이 정리 작업이 끝낸 실행은 폴링하지 않는다
            finish(runId, accepted.runId(), poll(accepted.runId()));
        } catch (AiUnavailableException e) {
            fallbackOrFail(runId, "연결 실패", "unavailable: " + e.getMessage(), null);
        } catch (AiRunNotFoundException e) {
            fallbackOrFail(runId, "실행 없음", "not found: " + e.getMessage(), null);
        } catch (ParticipantConsentRequiredException e) {
            // 요청 뒤 보호자 동의가 거둬졌다 — 결함이 아니라 예상된 상황이라 스택을 남기지 않는다
            failQuietly(runId, CoachRunFailureCode.CONSENT_REQUIRED, describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failQuietly(runId, CoachRunFailureCode.ERROR, "interrupted: 폴링 중 스레드가 멈췄다(서버 종료)");
        } catch (Exception e) {
            log.error("코치 실행 실패: run={}", runId, e);
            failQuietly(runId, CoachRunFailureCode.ERROR, describe(e));
        }
    }

    /** AI 가 끝낸 결과로 실행을 끝낸다. result 가 null 이면 maxPolls 안에 끝나지 않은 것이다. */
    private void finish(UUID runId, String aiRunId, @Nullable CoachRunResult result) {
        if (result == null) {
            fallbackOrFail(runId, "시간 초과", "timeout: AI run " + aiRunId + " 이 " + maxPolls + "회 폴링 안에 끝나지 않았다", null);
        } else if (result.refused() || result.status().equals("refused")) {
            String refusalReason = result.refusalReason();
            log.info("AI 가 편성을 거부했다: run={} reason={}", runId, refusalReason);
            pipeline.fail(
                    runId,
                    CoachRunFailureCode.NO_CITATIONS,
                    "refused: " + (refusalReason == null ? "unknown" : refusalReason),
                    steps(result),
                    true,
                    refusalReason);
        } else if (result.status().equals("succeeded") && result.proposal() != null) {
            pipeline.complete(runId, result);
        } else {
            // failed, 제안 없는 succeeded, 모르는 status — AI 실행 단위의 실패
            fallbackOrFail(
                    runId,
                    "편성 실패",
                    "failed: AI run status=" + result.status() + (result.proposal() == null ? " · 제안 없음" : ""),
                    steps(result));
        }
    }

    /**
     * 보드 F3 「LLM 없이도 돈다」 — 라벨만으로 편성한다. 고를 요인도 인용할 근거도 없으면 FAILED(AI_FAILED).
     * summary 는 대체 편성 단계 요약에 보일 짧은 까닭, detail 은 로그 · failure_reason 에 남길 원문이다.
     * aiSteps 는 근거가 없어 FAILED 로 끝낼 때 남길 AI 의 단계(없으면 저장된 단계를 그대로 둔다).
     * 이 메서드는 예외를 밖으로 내지 않는다 — RUNNING 으로 남으면 그 (프로필, 날짜)가 정리 작업 전까지 잠긴다.
     */
    private void fallbackOrFail(UUID runId, String summary, String detail, @Nullable List<CoachStep> aiSteps) {
        log.warn("AI 로 짜지 못해 라벨 기반 대체 편성: run={} ({})", runId, detail);
        try {
            CoachRunResult result = pipeline.planFallback(runId, summary);
            if (result == null) {
                pipeline.fail(
                        runId,
                        CoachRunFailureCode.AI_FAILED,
                        "AI 로 짜지 못했고(" + detail + ") 대체 편성 근거(측정 · 보호자가 키워 주고 싶은 역량)도 없다",
                        aiSteps,
                        false,
                        null);
            } else {
                pipeline.complete(runId, result);
            }
        } catch (RuntimeException failure) {
            log.error("대체 편성도 실패: run={}", runId, failure);
            failQuietly(
                    runId,
                    codeOf(failure, CoachRunFailureCode.AI_FAILED),
                    "AI 로 짜지 못한 뒤 대체 편성도 실패: " + describe(failure));
        }
    }

    private void failQuietly(UUID runId, CoachRunFailureCode code, String reason) {
        try {
            pipeline.fail(runId, code, reason, null, false, null);
        } catch (RuntimeException failure) {
            log.error("실패 기록도 실패: run={}", runId, failure);
        }
    }

    /** 보호자 동의가 거둬진 것이면 CONSENT_REQUIRED, 아니면 otherwise. */
    private static CoachRunFailureCode codeOf(Exception e, CoachRunFailureCode otherwise) {
        return e instanceof ParticipantConsentRequiredException ? CoachRunFailureCode.CONSENT_REQUIRED : otherwise;
    }

    private static String describe(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * 끝난 결과, 또는 maxPolls 안에 끝나지 않으면 null.
     * 한 번의 일시 오류(타임아웃 · 연결 실패 · 5xx → {@link AiUnavailableException})는 그 회차만 건너뛴다 —
     * AI 는 계속 짜고 있을 수 있어서 곧바로 대체 편성으로 가면 AI 결과를 버리게 된다.
     * 404(실행이 사라짐 — AI 재시작 · 다른 워커)는 {@link AiRunNotFoundException} 으로 그대로 던진다.
     */
    private @Nullable CoachRunResult poll(String aiRunId) throws InterruptedException {
        for (int attempt = 0; attempt < maxPolls; attempt++) {
            if (attempt > 0 && pollIntervalMs > 0) Thread.sleep(pollIntervalMs);
            CoachRunResult result;
            try {
                result = gateway.getCoachRun(aiRunId);
            } catch (AiUnavailableException e) {
                log.warn(
                        "AI 폴링 {}/{} 일시 오류 — 다음 폴링으로 넘긴다: ai run={} ({})",
                        attempt + 1,
                        maxPolls,
                        aiRunId,
                        e.getMessage());
                continue;
            }
            if (!result.isRunning()) return result;
        }
        return null;
    }

    private static List<CoachStep> steps(CoachRunResult result) {
        return ProposalConverter.steps(result);
    }
}
