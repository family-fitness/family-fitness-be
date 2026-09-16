package kr.ac.kookmin.familyfitness.coaching.domain;

import org.jspecify.annotations.Nullable;

/**
 * 서버가 계산한 진행도와 그 근거.
 * 정책({@link kr.ac.kookmin.familyfitness.coaching.application.MissionCompletionPolicy})이 만든다.
 */
public record MissionProgress(double progress, @Nullable VerifiedBy verifiedBy) {
    public static final MissionProgress NONE = new MissionProgress(0.0, null);

    public MissionProgress {
        if (progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("진행도는 0~1 이어야 한다: " + progress);
        }
    }

    public static MissionProgress of(Number achieved, int target, @Nullable VerifiedBy verifiedBy) {
        double ratio = target <= 0 ? 0.0 : achieved.doubleValue() / target;
        return new MissionProgress(Math.min(1.0, Math.max(0.0, ratio)), verifiedBy);
    }
}
