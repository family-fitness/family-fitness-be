package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** 칸 끝 한 행(V144). 넣기만 하고 고치지 않는다(수정자 없음). */
@Entity
@Table(name = "mission_session_completions")
public class MissionSessionCompletionEntity {
    @EmbeddedId
    private MissionSessionCompletionId id;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "active_seconds", nullable = false)
    private int activeSeconds;

    @Column(name = "verified_by", nullable = false, length = 20)
    private String verifiedBy;

    protected MissionSessionCompletionEntity() {}

    public MissionSessionCompletionEntity(
            MissionSessionCompletionId id,
            Instant completedAt,
            LocalDate completedOn,
            int activeSeconds,
            String verifiedBy) {
        this.id = id;
        this.completedAt = completedAt;
        this.completedOn = completedOn;
        this.activeSeconds = activeSeconds;
        this.verifiedBy = verifiedBy;
    }

    public MissionSessionCompletionId getId() {
        return id;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public int getActiveSeconds() {
        return activeSeconds;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }
}
