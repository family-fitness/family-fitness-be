package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 미션 참여자 한 명의 진행 상태. 진행도는 서버만 계산한다(0.0~1.0).
 * 서버가 검증할 수 있는 지표는 목표 도달 즉시 완료, `STEPS` 는 보호자 확인({@link #confirm})이 있어야 완료다.
 */
public class MissionParticipant {
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

    /** 새 진행도를 반영한다. 완료된 참여자는 진행도를 되돌리지 않는다. 바뀐 것이 있으면 true. */
    boolean apply(MissionProgress computed, boolean serverVerifiable, Instant at) {
        if (isCompleted()) return false;
        boolean changed = false;
        if (computed.progress() != progress) {
            progress = computed.progress();
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
