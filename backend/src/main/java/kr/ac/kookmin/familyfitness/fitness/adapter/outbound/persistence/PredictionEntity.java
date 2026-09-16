package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** `predictions` + `prediction_points`. AI 응답을 그대로 굳힌다. */
@Entity
@Table(name = "predictions")
public class PredictionEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "fitness_test_id")
    private @Nullable UUID fitnessTestId;

    @Column(name = "item_code", nullable = false, length = 3)
    private String itemCode;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "horizon_years", nullable = false)
    private int horizonYears;

    @Column(name = "model_version", nullable = false, length = 40)
    private String modelVersion;

    @Column(name = "basis", nullable = false, length = 60)
    private String basis;

    @Column(name = "notice", nullable = false, length = 300)
    private String notice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "prediction_points", joinColumns = @JoinColumn(name = "prediction_id"))
    private List<PredictionPointEmbeddable> points = new ArrayList<>();

    protected PredictionEntity() {}

    public PredictionEntity(
            UUID id,
            UUID profileId,
            @Nullable UUID fitnessTestId,
            String itemCode,
            int horizonYears,
            String modelVersion,
            String basis,
            String notice,
            Instant createdAt,
            List<PredictionPointEmbeddable> points) {
        this.id = id;
        this.profileId = profileId;
        this.fitnessTestId = fitnessTestId;
        this.itemCode = itemCode;
        this.horizonYears = horizonYears;
        this.modelVersion = modelVersion;
        this.basis = basis;
        this.notice = notice;
        this.createdAt = createdAt;
        this.points = new ArrayList<>(points);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public @Nullable UUID getFitnessTestId() {
        return fitnessTestId;
    }

    public String getItemCode() {
        return itemCode;
    }

    public int getHorizonYears() {
        return horizonYears;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getBasis() {
        return basis;
    }

    public String getNotice() {
        return notice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PredictionPointEmbeddable> getPoints() {
        return points;
    }
}
