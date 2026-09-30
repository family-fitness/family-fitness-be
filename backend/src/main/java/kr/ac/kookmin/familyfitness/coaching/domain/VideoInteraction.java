package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 프로필 한 명과 영상 하나의 관계: 즐겨찾기와 시청 진행률. (profile, video) 당 한 행.
 * 진행률은 최대값만 남고, 최초로 {@link #COMPLETION_THRESHOLD} 에 닿을 때 한 번만 활동 분을 적립한다({@link #isCreditable()}).
 */
public class VideoInteraction {
    public static final double COMPLETION_THRESHOLD = 0.9;

    private final UUID id;
    private final UUID profileId;
    private final String videoId;
    private boolean favorited;
    private @Nullable Instant favoritedAt;
    private double maxProgress;
    private int watchedSec;
    private @Nullable Instant creditedAt;
    private @Nullable Instant lastWatchedAt;
    private Instant updatedAt;

    private VideoInteraction(
            UUID id,
            UUID profileId,
            String videoId,
            boolean favorited,
            @Nullable Instant favoritedAt,
            double maxProgress,
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

    public boolean isFavorited() {
        return favorited;
    }

    public @Nullable Instant getFavoritedAt() {
        return favoritedAt;
    }

    public double getMaxProgress() {
        return maxProgress;
    }

    public int getWatchedSec() {
        return watchedSec;
    }

    public @Nullable Instant getCreditedAt() {
        return creditedAt;
    }

    public @Nullable Instant getLastWatchedAt() {
        return lastWatchedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCompleted() {
        return maxProgress >= COMPLETION_THRESHOLD;
    }

    public boolean isCreditable() {
        return isCompleted() && creditedAt == null;
    }

    public boolean isWatched() {
        return lastWatchedAt != null || maxProgress > 0.0;
    }

    public void setFavorite(boolean favorited, Instant at) {
        if (this.favorited == favorited) return;
        this.favorited = favorited;
        favoritedAt = favorited ? at : null;
        updatedAt = at;
    }

    /** 시청 보고. 진행률·시청 초는 최대값만 남긴다. */
    public void watch(double progress, int watchedSec, Instant at) {
        if (progress < 0.0 || progress > 1.0) throw new IllegalArgumentException("progress 는 0~1 이어야 한다");
        if (watchedSec < 0) throw new IllegalArgumentException("watchedSec 는 0 이상이어야 한다");
        maxProgress = Math.max(maxProgress, progress);
        this.watchedSec = Math.max(this.watchedSec, watchedSec);
        lastWatchedAt = at;
        updatedAt = at;
    }

    public void markCredited(Instant at) {
        creditedAt = at;
        updatedAt = at;
    }

    public static VideoInteraction start(UUID id, UUID profileId, String videoId, Instant at) {
        return new VideoInteraction(id, profileId, videoId, false, null, 0.0, 0, null, null, at);
    }

    public static VideoInteraction reconstitute(
            UUID id,
            UUID profileId,
            String videoId,
            boolean favorited,
            @Nullable Instant favoritedAt,
            double maxProgress,
            int watchedSec,
            @Nullable Instant creditedAt,
            @Nullable Instant lastWatchedAt,
            Instant updatedAt) {
        return new VideoInteraction(
                id,
                profileId,
                videoId,
                favorited,
                favoritedAt,
                maxProgress,
                watchedSec,
                creditedAt,
                lastWatchedAt,
                updatedAt);
    }
}
