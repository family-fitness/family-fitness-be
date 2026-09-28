package kr.ac.kookmin.familyfitness.coaching.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 미션 참여자 한 명의 진행 상태. 진행도는 서버만 계산한다(0.0~1.0).
 * 서버가 검증할 수 있는 지표는 목표 도달 즉시 완료, `STEPS` 는 보호자 확인({@link #confirm})이 있어야 완료다.
 */
public class MissionParticipant {
    /** 진행도 저장 정밀도. mission_participants.progress 가 numeric(4,3) 이고 소수 셋째 자리 아래는 버린다. */
    public static final int PROGRESS_SCALE = 3;

    private final UUID profileId;
    private final @Nullable String coachRole;
    private double progress;
    private ParticipantStatus status;
    private @Nullable VerifiedBy verifiedBy;
    private @Nullable Instant verifiedAt;
    private @Nullable UUID confirmedBy;
    private Instant updatedAt;

    private MissionParticipant(
            UUID profileId,
            @Nullable String coachRole,
            double progress,
            ParticipantStatus status,
            @Nullable VerifiedBy verifiedBy,
            @Nullable Instant verifiedAt,
            @Nullable UUID confirmedBy,
            Instant updatedAt) {
        this.profileId = profileId;
        this.coachRole = coachRole;
        this.progress = progress;
        this.status = status;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = verifiedAt;
        this.confirmedBy = confirmedBy;
        this.updatedAt = updatedAt;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public @Nullable String getCoachRole() {
        return coachRole;
    }

    public double getProgress() {
        return progress;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public @Nullable VerifiedBy getVerifiedBy() {
        return verifiedBy;
    }

    public @Nullable Instant getVerifiedAt() {
        return verifiedAt;
    }

    public @Nullable UUID getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCompleted() {
        return status == ParticipantStatus.COMPLETED;
    }

    public boolean isReachedTarget() {
        return progress >= 1.0;
    }

    /** 목표에는 도달했지만 사람이 말한 값이라 보호자 확인이 남았다. */
    public boolean isNeedsGuardianCheck() {
        return isReachedTarget() && !isCompleted();
    }

    /**
     * 새 진행도를 반영한다. 완료된 참여자는 진행도를 되돌리지 않는다. 바뀐 것이 있으면 true.
     * 진행도는 저장 정밀도(소수 셋째 자리 버림)로 맞춰 비교 · 보관한다. 1/3 처럼 끝나지 않는 값을 그대로 비교하면
     * DB 에서 읽은 0.333 과 계산한 0.3333… 이 늘 달라 읽을 때마다 같은 값을 다시 저장했다.
     * 확인 방법(verifiedBy)은 조금이라도 진행했으면 그 근거를 싣는다 — 칸 하나만 끝내도 끝낸 칸 옆에 「영상으로 확인됨」 이
     * 보인다(FE 목 handlers.ts 칸 끝, FE 요청서 4장). 확인 시각(verifiedAt)은 완료될 때만 적는다.
     */
    boolean apply(MissionProgress computed, boolean serverVerifiable, Instant at) {
        if (isCompleted()) return false;
        boolean changed = false;
        double next = atStoredScale(computed.progress());
        if (next != progress) {
            progress = next;
            changed = true;
        }
        VerifiedBy nextVerifiedBy = next > 0 ? computed.verifiedBy() : null;
        if (nextVerifiedBy != verifiedBy) {
            verifiedBy = nextVerifiedBy;
            changed = true;
        }
        if (serverVerifiable && isReachedTarget()) {
            status = ParticipantStatus.COMPLETED;
            verifiedBy = computed.verifiedBy();
            verifiedAt = at;
            changed = true;
        }
        if (changed) updatedAt = at;
        return changed;
    }

    /** 보호자 확인. 목표 도달 전에는 {@link TargetNotReachedException}. 이미 완료면 그대로 둔다. */
    void confirm(UUID by, Instant at) {
        if (isCompleted()) return;
        if (!isReachedTarget()) throw new TargetNotReachedException(progress);
        status = ParticipantStatus.COMPLETED;
        verifiedBy = VerifiedBy.SELF_REPORT;
        verifiedAt = at;
        confirmedBy = by;
        updatedAt = at;
    }

    /** 저장 정밀도로 맞춘 진행도(소수 셋째 자리 아래 버림). 저장소도 같은 규칙으로 쓴다. */
    public static BigDecimal storedProgress(double value) {
        return BigDecimal.valueOf(value).setScale(PROGRESS_SCALE, RoundingMode.DOWN);
    }

    private static double atStoredScale(double value) {
        return storedProgress(value).doubleValue();
    }

    public static MissionParticipant pending(UUID profileId, @Nullable String coachRole, Instant at) {
        return new MissionParticipant(profileId, coachRole, 0.0, ParticipantStatus.PENDING, null, null, null, at);
    }

    public static MissionParticipant reconstitute(
            UUID profileId,
            @Nullable String coachRole,
            double progress,
            ParticipantStatus status,
            @Nullable VerifiedBy verifiedBy,
            @Nullable Instant verifiedAt,
            @Nullable UUID confirmedBy,
            Instant updatedAt) {
        return new MissionParticipant(
                profileId, coachRole, progress, status, verifiedBy, verifiedAt, confirmedBy, updatedAt);
    }
}
