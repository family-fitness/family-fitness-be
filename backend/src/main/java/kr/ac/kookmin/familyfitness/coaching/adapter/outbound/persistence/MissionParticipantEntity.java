package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "mission_participants")
public class MissionParticipantEntity {
    @EmbeddedId
    private MissionParticipantId id;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "progress", nullable = false, precision = 4, scale = 3)
    private BigDecimal progress;

    @Column(name = "verified_by", length = 20)
    private @Nullable String verifiedBy;

    @Column(name = "verified_at")
    private @Nullable Instant verifiedAt;

    @Column(name = "confirmed_by_profile_id")
    private @Nullable UUID confirmedByProfileId;

    @Column(name = "coach_role", length = 10)
    private @Nullable String coachRole;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MissionParticipantEntity() {}

    public MissionParticipantEntity(
            MissionParticipantId id,
            String status,
            BigDecimal progress,
            @Nullable String verifiedBy,
            @Nullable Instant verifiedAt,
            @Nullable UUID confirmedByProfileId,
            @Nullable String coachRole,
            Instant updatedAt) {
        this.id = id;
        this.status = status;
        this.progress = progress;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = verifiedAt;
        this.confirmedByProfileId = confirmedByProfileId;
        this.coachRole = coachRole;
        this.updatedAt = updatedAt;
    }

    public MissionParticipantId getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getProgress() {
        return progress;
    }

    public void setProgress(BigDecimal progress) {
        this.progress = progress;
    }

    public @Nullable String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(@Nullable String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public @Nullable Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(@Nullable Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public @Nullable UUID getConfirmedByProfileId() {
        return confirmedByProfileId;
    }

    public void setConfirmedByProfileId(@Nullable UUID confirmedByProfileId) {
        this.confirmedByProfileId = confirmedByProfileId;
    }

    public @Nullable String getCoachRole() {
        return coachRole;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
