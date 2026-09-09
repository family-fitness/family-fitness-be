package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.CursorPage
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

enum class VideoListType {
    ALL,
    FAVORITES,
    RECENT,
}

data class VideoListQuery(
    val list: VideoListType = VideoListType.ALL,
    val profileId: UUID?,
    val ageGroup: AgeGroup?,
    val factor: String?,
    val cursor: String?,
    val size: Int = 20,
)

data class VideoListView(
    val videos: List<VideoView>,
    val nextCursor: String?,
)

data class VideoView(
    val videoId: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val durationSec: Int?,
    val label: VideoLabel,
    val badges: List<String>,
    val favorited: Boolean,
    val maxProgress: Double?,
)

data class FavoriteCommand(
    val profileId: UUID,
    val favorited: Boolean,
)

data class FavoriteView(
    val videoId: String,
    val profileId: UUID,
    val favorited: Boolean,
    val favoritedAt: Instant?,
)

data class VideoProgressCommand(
    val profileId: UUID,
    val progress: Double,
    val watchedSec: Int,
    val missionId: UUID?,
)

data class VideoProgressView(
    val maxProgress: Double,
    val completed: Boolean,
    val creditedMinutes: Int,
    val verifiedBy: VerifiedBy?,
    val missionProgress: Double?,
)

/** 영상 목록·즐겨찾기·시청 진행률. 완주(≥0.9) 시 영상 길이만큼 활동 분을 한 번만 적립한다. */
@Service
class VideoService(
    private val videos: ExerciseVideoRepository,
    private val interactions: VideoInteractionRepository,
    private val missions: MissionRepository,
    private val familyAccess: FamilyAccess,
    private val recorder: ActivityRecorder,
    private val policy: MissionCompletionPolicy,
    private val time: AppTime,
) {
    /**
     * `ALL`·`FAVORITES` 는 videoId 오름차순 커서 페이지, `RECENT` 는 최근 시청순이며 커서를 무시한다.
     * `ageGroup` 은 안전 필터(라벨 없는 영상은 아이 연령대에 나가지 않음), `factor` 는 라벨 요인 포함 여부.
     */
    @Transactional(readOnly = true)
    fun list(
        userId: UUID,
        query: VideoListQuery,
    ): VideoListView {
        require(query.size in 1..MAX_PAGE_SIZE) { "size 는 1~$MAX_PAGE_SIZE 이어야 합니다" }
        if (query.list != VideoListType.ALL) requireNotNull(query.profileId) { "${query.list} 목록에는 profileId 가 필요합니다" }
        query.profileId?.let { familyAccess.requireSameFamilyAsProfile(userId, it) }
        val mine = query.profileId?.let { pid -> interactions.findAllOf(pid).associateBy { it.videoId } }.orEmpty()

        val page: CursorPage<ExerciseVideo> =
            when (query.list) {
                VideoListType.ALL -> {
                    CursorPage.of(
                        videos.findAllAfter(query.cursor).filter { it.matches(query.ageGroup, query.factor) },
                        query.size,
                    ) { it.videoId }
                }

                VideoListType.FAVORITES -> {
                    val ids =
                        mine.values
                            .filter { it.favorited }
                            .map { it.videoId }
                            .filter { query.cursor == null || it > query.cursor }
                    val sorted = videos.findAllByIds(ids).filter { it.matches(query.ageGroup, query.factor) }.sortedBy { it.videoId }
                    CursorPage.of(sorted, query.size) { it.videoId }
                }

                VideoListType.RECENT -> {
                    val recent = mine.values.filter { it.watched }.sortedByDescending { it.lastWatchedAt ?: Instant.EPOCH }
                    val byId = videos.findAllByIds(recent.map { it.videoId }).associateBy { it.videoId }
                    val ordered = recent.mapNotNull { byId[it.videoId] }.filter { it.matches(query.ageGroup, query.factor) }
                    CursorPage(ordered.take(query.size), null)
                }
            }
        return VideoListView(
            videos =
                page.items.map { v ->
                    val i = mine[v.videoId]
                    VideoView(
                        videoId = v.videoId,
                        title = v.title,
                        url = v.url,
                        thumbnailUrl = v.thumbnailUrl,
                        durationSec = v.durationSec,
                        label = v.label,
                        badges = v.badges,
                        favorited = i?.favorited ?: false,
                        maxProgress = i?.maxProgress,
                    )
                },
            nextCursor = page.nextCursor,
        )
    }

    @Transactional
    fun favorite(
        userId: UUID,
        videoId: String,
        command: FavoriteCommand,
    ): FavoriteView {
        videos.findById(videoId) ?: throw VideoNotFoundException(videoId)
        familyAccess.requireSameFamilyAsProfile(userId, command.profileId)
        val now = time.now()
        val interaction =
            interactions.find(command.profileId, videoId) ?: VideoInteraction.start(UUID.randomUUID(), command.profileId, videoId, now)
        interaction.setFavorite(command.favorited, now)
        interactions.save(interaction)
        return FavoriteView(videoId, command.profileId, interaction.favorited, interaction.favoritedAt)
    }

    /**
     * 진행률 보고. 최대값만 남기고, 최초로 0.9 이상이 되면 `activity_daily`(VIDEO) 에 영상 길이(분, 올림)를 1회 적립한다.
     * missionId 가 있으면 그 미션의 참여자 진행도도 갱신한다.
     */
    @Transactional
    fun progress(
        userId: UUID,
        videoId: String,
        command: VideoProgressCommand,
    ): VideoProgressView {
        val video = videos.findById(videoId) ?: throw VideoNotFoundException(videoId)
        familyAccess.requireSameFamilyAsProfile(userId, command.profileId)
        val now = time.now()
        val interaction =
            interactions.find(command.profileId, videoId) ?: VideoInteraction.start(UUID.randomUUID(), command.profileId, videoId, now)
        interaction.watch(command.progress, command.watchedSec, now)

        var credited = 0
        if (interaction.creditable) {
            credited = video.creditMinutes
            if (credited > 0) recorder.addActiveMinutes(command.profileId, time.today(), ActivitySource.VIDEO, credited)
            interaction.markCredited(now)
        }
        interactions.save(interaction)

        val missionProgress =
            command.missionId?.let { missionId ->
                val mission = missions.findById(missionId) ?: throw MissionNotFoundException(missionId)
                mission.participantOf(command.profileId)
                policy.refreshParticipant(mission, command.profileId, now).participantOf(command.profileId).progress
            }
        return VideoProgressView(
            maxProgress = interaction.maxProgress,
            completed = interaction.completed,
            creditedMinutes = credited,
            verifiedBy = if (interaction.completed) VerifiedBy.VIDEO_PROGRESS else null,
            missionProgress = missionProgress,
        )
    }

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
