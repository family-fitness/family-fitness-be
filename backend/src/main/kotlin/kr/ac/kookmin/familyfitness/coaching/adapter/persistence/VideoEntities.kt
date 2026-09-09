package kr.ac.kookmin.familyfitness.coaching.adapter.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/** `exercise_videos` 행. 수집 배치가 채우고 이 모듈은 읽기만 한다. */
@Entity
@Table(name = "exercise_videos")
class ExerciseVideoEntity(
    @Id
    @Column(name = "video_id", length = 32)
    val videoId: String,
    @Column(name = "title", nullable = false, length = 300)
    val title: String,
    @Column(name = "channel_name", nullable = false, length = 120)
    val channelName: String,
    @Column(name = "channel_type", nullable = false, length = 20)
    val channelType: String,
    @Column(name = "duration_sec")
    val durationSec: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_from")
    val ageFrom: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_to")
    val ageTo: Int?,
    @Column(name = "factors", length = 120)
    val factors: String?,
    @Column(name = "intensity", length = 10)
    val intensity: String?,
    @Column(name = "space", length = 20)
    val space: String?,
    @Column(name = "noise", length = 10)
    val noise: String?,
    @Column(name = "equipment", length = 60)
    val equipment: String?,
    @Column(name = "labeled_by", nullable = false, length = 20)
    val labeledBy: String,
    @Column(name = "label_model", length = 40)
    val labelModel: String?,
    @Column(name = "collected_at", nullable = false)
    val collectedAt: Instant,
) {
    fun toDomain(): ExerciseVideo =
        ExerciseVideo(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelType = channelType,
            durationSec = durationSec,
            label = VideoLabel(ageFrom, ageTo, VideoLabel.parseFactors(factors), intensity, space, noise, labelModel),
            equipment = equipment,
            labeledBy = labeledBy,
            collectedAt = collectedAt,
        )
}

@Entity
@Table(name = "video_interactions")
class VideoInteractionEntity(
    @Id
    @Column(name = "id")
    val id: UUID,
    @Column(name = "profile_id", nullable = false)
    val profileId: UUID,
    @Column(name = "video_id", nullable = false, length = 32)
    val videoId: String,
    @Column(name = "favorited", nullable = false)
    var favorited: Boolean,
    @Column(name = "favorited_at")
    var favoritedAt: Instant?,
    @Column(name = "max_progress", nullable = false, precision = 4, scale = 3)
    var maxProgress: BigDecimal,
    @Column(name = "watched_sec", nullable = false)
    var watchedSec: Int,
    @Column(name = "credited_at")
    var creditedAt: Instant?,
    @Column(name = "last_watched_at")
    var lastWatchedAt: Instant?,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    fun toDomain(): VideoInteraction =
        VideoInteraction.reconstitute(
            id,
            profileId,
            videoId,
            favorited,
            favoritedAt,
            maxProgress.toDouble(),
            watchedSec,
            creditedAt,
            lastWatchedAt,
            updatedAt,
        )

    fun applyFrom(domain: VideoInteraction) {
        favorited = domain.favorited
        favoritedAt = domain.favoritedAt
        maxProgress = ratio(domain.maxProgress)
        watchedSec = domain.watchedSec
        creditedAt = domain.creditedAt
        lastWatchedAt = domain.lastWatchedAt
        updatedAt = domain.updatedAt
    }

    companion object {
        fun ratio(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(3, RoundingMode.DOWN)

        fun from(domain: VideoInteraction): VideoInteractionEntity =
            VideoInteractionEntity(
                id = domain.id,
                profileId = domain.profileId,
                videoId = domain.videoId,
                favorited = domain.favorited,
                favoritedAt = domain.favoritedAt,
                maxProgress = ratio(domain.maxProgress),
                watchedSec = domain.watchedSec,
                creditedAt = domain.creditedAt,
                lastWatchedAt = domain.lastWatchedAt,
                updatedAt = domain.updatedAt,
            )
    }
}
