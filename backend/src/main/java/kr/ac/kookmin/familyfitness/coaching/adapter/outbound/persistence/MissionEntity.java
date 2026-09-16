package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "missions")
public class MissionEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "coach_run_id")
    private @Nullable UUID coachRunId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 400)
    private @Nullable String description;

    @Column(name = "origin", nullable = false, length = 10)
    private String origin;

    @Column(name = "target_metric", nullable = false, length = 20)
    private String targetMetric;

    @Column(name = "target_value", nullable = false)
    private int targetValue;

    @Column(name = "video_id", length = 32)
    private @Nullable String videoId;

    @Column(name = "video_start_sec")
    private @Nullable Integer videoStartSec;

    @Column(name = "rationale", length = 400)
    private @Nullable String rationale;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "created_by")
    private @Nullable UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MissionEntity() {}

    public MissionEntity(
            UUID id,
            UUID familyId,
            @Nullable UUID coachRunId,
            String title,
            @Nullable String description,
            String origin,
            String targetMetric,
            int targetValue,
            @Nullable String videoId,
            @Nullable Integer videoStartSec,
            @Nullable String rationale,
            LocalDate startsOn,
            LocalDate endsOn,
            @Nullable UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.coachRunId = coachRunId;
        this.title = title;
        this.description = description;
        this.origin = origin;
        this.targetMetric = targetMetric;
        this.targetValue = targetValue;
        this.videoId = videoId;
        this.videoStartSec = videoStartSec;
        this.rationale = rationale;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public @Nullable UUID getCoachRunId() {
        return coachRunId;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public String getOrigin() {
        return origin;
    }

    public String getTargetMetric() {
        return targetMetric;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public @Nullable String getVideoId() {
        return videoId;
    }

    public @Nullable Integer getVideoStartSec() {
        return videoStartSec;
    }

    public @Nullable String getRationale() {
        return rationale;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public @Nullable UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
