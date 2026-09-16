package kr.ac.kookmin.familyfitness.fitness.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** AI `trajectory` 응답을 그대로 굳힌 예측. 기준 측정 회차가 삭제돼도 남도록 fitnessTestId 는 참조만 한다. */
public class Prediction {
    /** AI 응답에 model version 이 없어 고정한다 (▲ 확정 필요). */
    public static final String MODEL_VERSION = "ai-trajectory-v1";

    private final UUID id;
    private final UUID profileId;
    private final @Nullable UUID fitnessTestId;
    private final String itemCode;
    private final int horizonYears;
    private final String modelVersion;
    private final String basis;
    private final String notice;
    private final List<PredictionPoint> points;
    private final Instant createdAt;

    public Prediction(
            UUID id,
            UUID profileId,
            @Nullable UUID fitnessTestId,
            String itemCode,
            int horizonYears,
            String modelVersion,
            String basis,
            String notice,
            List<PredictionPoint> points,
            Instant createdAt) {
        this.id = id;
        this.profileId = profileId;
        this.fitnessTestId = fitnessTestId;
        this.itemCode = itemCode;
        this.horizonYears = horizonYears;
        this.modelVersion = modelVersion;
        this.basis = basis;
        this.notice = notice;
        this.points = points;
        this.createdAt = createdAt;
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

    public List<PredictionPoint> getPoints() {
        return points;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
