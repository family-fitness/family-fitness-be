package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "video_interactions")
public class VideoInteractionEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "video_id", nullable = false, length = 32)
    private String videoId;

    @Column(name = "favorited", nullable = false)
    private boolean favorited;

    @Column(name = "favorited_at")
    private @Nullable Instant favoritedAt;

    @Column(name = "max_progress", nullable = false, precision = 4, scale = 3)
    private BigDecimal maxProgress;

    @Column(name = "watched_sec", nullable = false)
    private int watchedSec;

    @Column(name = "credited_at")
    private @Nullable Instant creditedAt;

    @Column(name = "last_watched_at")
    private @Nullable Instant lastWatchedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VideoInteractionEntity() {}

    public VideoInteractionEntity(
            UUID id,
            UUID profileId,
            String videoId,
            boolean favorited,
            @Nullable Instant favoritedAt,
            BigDecimal maxProgress,
            int watchedSec,
            @Nullable Instant creditedAt,
            @Nullable Instant lastWatchedAt,
            Instant updatedAt) {
        this.id = id;
        this.profileId = profileId;
        this.videoId = videoId;
        this.favorited = favorited;
        this.favoritedAt = favoritedAt;
        this.maxProgress = maxProgress;
        this.watchedSec = watchedSec;
        this.creditedAt = creditedAt;
        this.lastWatchedAt = lastWatchedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getVideoId() {
        return videoId;
    }

    public VideoInteraction toDomain() {
        return VideoInteraction.reconstitute(
                id,
                profileId,
                videoId,
                favorited,
                favoritedAt,
                maxProgress.doubleValue(),
                watchedSec,
                creditedAt,
                lastWatchedAt,
                updatedAt);
    }

    public void applyFrom(VideoInteraction domain) {
        favorited = domain.isFavorited();
        favoritedAt = domain.getFavoritedAt();
        maxProgress = ratio(domain.getMaxProgress());
        watchedSec = domain.getWatchedSec();
        creditedAt = domain.getCreditedAt();
        lastWatchedAt = domain.getLastWatchedAt();
        updatedAt = domain.getUpdatedAt();
    }

    public static BigDecimal ratio(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.DOWN);
    }

    public static VideoInteractionEntity from(VideoInteraction domain) {
        return new VideoInteractionEntity(
                domain.getId(),
                domain.getProfileId(),
                domain.getVideoId(),
                domain.isFavorited(),
                domain.getFavoritedAt(),
                ratio(domain.getMaxProgress()),
                domain.getWatchedSec(),
                domain.getCreditedAt(),
                domain.getLastWatchedAt(),
                domain.getUpdatedAt());
    }
}
