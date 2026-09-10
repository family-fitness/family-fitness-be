package kr.ac.kookmin.familyfitness.coaching.domain

import java.time.Instant
import java.util.UUID
import kotlin.math.max

/**
 * 프로필 한 명과 영상 하나의 관계: 즐겨찾기와 시청 진행률. (profile, video) 당 한 행.
 * 진행률은 최대값만 남고, 최초로 [COMPLETION_THRESHOLD] 에 닿을 때 한 번만 활동 분을 적립한다([creditable]).
 */
class VideoInteraction private constructor(
    val id: UUID,
    val profileId: UUID,
    val videoId: String,
    favorited: Boolean,
    favoritedAt: Instant?,
    maxProgress: Double,
    watchedSec: Int,
    creditedAt: Instant?,
    lastWatchedAt: Instant?,
    updatedAt: Instant,
) {
    var favorited: Boolean = favorited
        private set
    var favoritedAt: Instant? = favoritedAt
        private set
    var maxProgress: Double = maxProgress
        private set
    var watchedSec: Int = watchedSec
        private set
    var creditedAt: Instant? = creditedAt
        private set
    var lastWatchedAt: Instant? = lastWatchedAt
        private set
    var updatedAt: Instant = updatedAt
        private set

    val completed: Boolean get() = maxProgress >= COMPLETION_THRESHOLD
    val creditable: Boolean get() = completed && creditedAt == null
    val watched: Boolean get() = lastWatchedAt != null || maxProgress > 0.0

    fun setFavorite(
        favorited: Boolean,
        at: Instant,
    ) {
        if (this.favorited == favorited) return
        this.favorited = favorited
        favoritedAt = if (favorited) at else null
        updatedAt = at
    }

    /** 시청 보고. 진행률·시청 초는 최대값만 남긴다. */
    fun watch(
        progress: Double,
        watchedSec: Int,
        at: Instant,
    ) {
        require(progress in 0.0..1.0) { "progress 는 0~1 이어야 한다" }
        require(watchedSec >= 0) { "watchedSec 는 0 이상이어야 한다" }
        maxProgress = max(maxProgress, progress)
        this.watchedSec = max(this.watchedSec, watchedSec)
        lastWatchedAt = at
        updatedAt = at
    }

    fun markCredited(at: Instant) {
        creditedAt = at
        updatedAt = at
    }

    companion object {
        const val COMPLETION_THRESHOLD = 0.9

        fun start(
            id: UUID,
            profileId: UUID,
            videoId: String,
            at: Instant,
        ): VideoInteraction = VideoInteraction(id, profileId, videoId, false, null, 0.0, 0, null, null, at)

        fun reconstitute(
            id: UUID,
            profileId: UUID,
            videoId: String,
            favorited: Boolean,
            favoritedAt: Instant?,
            maxProgress: Double,
            watchedSec: Int,
            creditedAt: Instant?,
            lastWatchedAt: Instant?,
            updatedAt: Instant,
        ): VideoInteraction =
            VideoInteraction(id, profileId, videoId, favorited, favoritedAt, maxProgress, watchedSec, creditedAt, lastWatchedAt, updatedAt)
    }
}
