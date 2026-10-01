package kr.ac.kookmin.familyfitness.progress.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 한 사람이 칸 하나를 끝냈다. coaching 이 칸 끝에서 만들어 {@link ProgressRecorder#sessionDone} 에 넘긴다.
 *
 * @param phase 칸 단계. 업적 「준비운동부터 정리운동까지」 를 가른다
 * @param factor 운동 1개가 기르는 체력 요인. 없으면 null. 업적 「여섯 가지 체력 요인」 판정에 쓰려고 원장에 같이 남긴다
 * @param missionCompleted 이 칸으로 이 사람의 미션이 끝났는가(참여자 상태가 COMPLETED)
 * @param verifiedBy 이 사람의 미션이 확인된 방법. 아직 안 끝났으면 null
 * @param completedOn 칸을 끝낸 날(KST). 서버가 받은 날이다(결정 22)
 * @param completedAt 칸을 끝낸 시각. 새로 받은 업적의 earnedAt 이 된다
 */
public record SessionDone(
        UUID familyId,
        UUID profileId,
        UUID missionId,
        int position,
        Phase phase,
        @Nullable FitnessFactor factor,
        boolean missionCompleted,
        @Nullable Verification verifiedBy,
        LocalDate completedOn,
        Instant completedAt) {
    public SessionDone {
        if (position < 1) throw new IllegalArgumentException("칸 번호(position)는 1부터다");
    }

    /** 끝까지 한 미션의 +20 을 주는가 — 서버가 잰 확인(타이머 · 영상)으로 끝났을 때만. */
    public boolean countsAsMissionDone() {
        return missionCompleted && verifiedBy != null && verifiedBy.isServerMeasured();
    }

    /** 칸 단계. coaching.domain.SessionPhase 와 이름이 같아 이름으로 옮긴다. */
    public enum Phase {
        WARMUP,
        MAIN,
        COOLDOWN
    }

    /** 미션이 확인된 방법. coaching.domain.VerifiedBy 와 이름이 같아 이름으로 옮긴다. */
    public enum Verification {
        VIDEO_PROGRESS,
        TIMER,
        SELF_REPORT;

        /** 서버가 직접 잰 확인인가. 걸음수 같은 자기 신고(SELF_REPORT)는 아니다(FE 규칙 2). */
        public boolean isServerMeasured() {
            return this != SELF_REPORT;
        }
    }
}
