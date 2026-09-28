package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 경험치 원장 한 행. 한 번 쓰고 고치지 않는다(수정자 없음). */
@Entity
@Table(name = "progress_xp_events")
public class XpEventEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "source_key", nullable = false, length = 80)
    private String sourceKey;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "from_profile_id")
    private @Nullable UUID fromProfileId;

    @Column(name = "mission_id")
    private @Nullable UUID missionId;

    @Column(name = "phase", length = 10)
    private @Nullable String phase;

    /** 한글 요인 이름(예: 유연성) — mission_sessions.factor 와 같다 */
    @Column(name = "factor", length = 20)
    private @Nullable String factor;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected XpEventEntity() {}

    public XpEventEntity(
            UUID id,
            UUID profileId,
            String kind,
            String sourceKey,
            int amount,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            @Nullable String phase,
            @Nullable String factor,
            LocalDate occurredOn,
            Instant createdAt) {
        this.id = id;
        this.profileId = profileId;
        this.kind = kind;
        this.sourceKey = sourceKey;
        this.amount = amount;
        this.fromProfileId = fromProfileId;
        this.missionId = missionId;
        this.phase = phase;
        this.factor = factor;
        this.occurredOn = occurredOn;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getKind() {
        return kind;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public int getAmount() {
        return amount;
    }

    public @Nullable UUID getFromProfileId() {
        return fromProfileId;
    }

    public @Nullable UUID getMissionId() {
        return missionId;
    }

    public @Nullable String getPhase() {
        return phase;
    }

    public @Nullable String getFactor() {
        return factor;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
