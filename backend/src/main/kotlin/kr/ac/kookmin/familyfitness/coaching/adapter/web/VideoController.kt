package kr.ac.kookmin.familyfitness.coaching.adapter.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.coaching.application.FavoriteCommand
import kr.ac.kookmin.familyfitness.coaching.application.FavoriteView
import kr.ac.kookmin.familyfitness.coaching.application.VideoListQuery
import kr.ac.kookmin.familyfitness.coaching.application.VideoListType
import kr.ac.kookmin.familyfitness.coaching.application.VideoListView
import kr.ac.kookmin.familyfitness.coaching.application.VideoProgressCommand
import kr.ac.kookmin.familyfitness.coaching.application.VideoProgressView
import kr.ac.kookmin.familyfitness.coaching.application.VideoService
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 운동 영상 목록·즐겨찾기·시청 진행률. */
@RestController
@RequestMapping("/api/v1/videos")
class VideoController(
    private val service: VideoService,
) {
    @GetMapping
    fun list(
        user: CurrentUser,
        @RequestParam(defaultValue = "ALL") list: VideoListType,
        @RequestParam(required = false) profileId: UUID?,
        @RequestParam(required = false) ageGroup: String?,
        @RequestParam(required = false) factor: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): VideoListView =
        service.list(
            user.userId,
            VideoListQuery(
                list = list,
                profileId = profileId,
                ageGroup = ageGroup?.takeIf { it.isNotBlank() }?.let(AgeGroup::fromLabel),
                factor = factor?.takeIf { it.isNotBlank() }?.let { FitnessFactor.fromLabel(it).label },
                cursor = cursor?.takeIf { it.isNotBlank() },
                size = size,
            ),
        )

    @PostMapping("/{videoId}/favorite")
    fun favorite(
        user: CurrentUser,
        @PathVariable videoId: String,
        @Valid @RequestBody body: FavoriteRequest,
    ): FavoriteView = service.favorite(user.userId, videoId, FavoriteCommand(body.profileId, body.favorited))

    @PostMapping("/{videoId}/progress")
    fun progress(
        user: CurrentUser,
        @PathVariable videoId: String,
        @Valid @RequestBody body: VideoProgressRequest,
    ): VideoProgressView =
        service.progress(user.userId, videoId, VideoProgressCommand(body.profileId, body.progress, body.watchedSec, body.missionId))
}
