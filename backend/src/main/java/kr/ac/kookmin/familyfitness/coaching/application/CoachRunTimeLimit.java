package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Duration;
import java.time.Instant;
import kr.ac.kookmin.familyfitness.shared.ai.HttpAiGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 한 코치 실행이 정상으로 RUNNING 에 머물 수 있는 가장 긴 시간. 새 값을 두지 않고 기존 설정에서 계산한다.
 * = AI 시작 호출 한도(연결 + 읽기) + 최대 폴링 수 × (폴링 간격 + 폴링 한 번의 호출 한도).
 * 기본값은 3s + 40 × (1.5s + 4s) = 223s. 이보다 오래된 RUNNING 은 이 프로세스가 끝내지 못한 실행(재시작 · 강제 종료)이다.
 */
@Component
public class CoachRunTimeLimit {
    private final Duration longestRunning;

    public CoachRunTimeLimit(
            @Value("${app.coach.poll-interval-ms:1500}") long pollIntervalMs,
            @Value("${app.coach.max-polls:40}") int maxPolls) {
        Duration startCall = HttpAiGateway.CONNECT_TIMEOUT.plus(HttpAiGateway.RUN_START_READ_TIMEOUT);
        Duration pollCall = HttpAiGateway.CONNECT_TIMEOUT.plus(HttpAiGateway.RUN_POLL_READ_TIMEOUT);
        this.longestRunning =
                startCall.plus(Duration.ofMillis(pollIntervalMs).plus(pollCall).multipliedBy(maxPolls));
    }

    public Duration longestRunning() {
        return longestRunning;
    }

    /** 이 시각보다 먼저 만들어진 RUNNING 은 멈춘 실행으로 본다. */
    public Instant staleBefore(Instant now) {
        return now.minus(longestRunning);
    }

    /** 멈춘 실행을 FAILED 로 바꿀 때 남기는 사유. */
    public String staleReason() {
        return "stale: " + longestRunning.toSeconds() + "초 넘게 RUNNING 이라 끝내지 못한 실행으로 정리했다(서버 재시작 · 강제 종료)";
    }
}
