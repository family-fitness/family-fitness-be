package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;

/**
 * 커밋 후 {@link CoachRunExecutor} 가 받아 편성을 진행한다. {@code labelsOnly} 면 AI 를 부르지 않고 라벨 대체 편성으로 짠다 —
 * 심사용 계정을 모두 합친 오늘 AI 몫이 끝났을 때다({@link ReviewRunQuota}).
 */
public record CoachRunRequested(UUID runId, boolean labelsOnly) {
    /** AI 로 짜는 보통 편성. */
    public CoachRunRequested(UUID runId) {
        this(runId, false);
    }
}
