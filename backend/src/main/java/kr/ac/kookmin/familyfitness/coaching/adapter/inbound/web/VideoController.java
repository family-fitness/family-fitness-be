package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.FavoriteCommand;
import kr.ac.kookmin.familyfitness.coaching.application.FavoriteView;
import kr.ac.kookmin.familyfitness.coaching.application.VideoListQuery;
import kr.ac.kookmin.familyfitness.coaching.application.VideoListType;
import kr.ac.kookmin.familyfitness.coaching.application.VideoListView;
import kr.ac.kookmin.familyfitness.coaching.application.VideoProgressCommand;
import kr.ac.kookmin.familyfitness.coaching.application.VideoProgressView;
import kr.ac.kookmin.familyfitness.coaching.application.VideoService;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운동 영상 목록·즐겨찾기·시청 진행률. */
@RestController
@RequestMapping("/api/v1/videos")
public class VideoController {
    private final VideoService service;

    public VideoController(VideoService service) {
        this.service = service;
    }

    @GetMapping
    public VideoListView list(
            CurrentUser user,
            @RequestParam(defaultValue = "ALL") VideoListType list,
            @RequestParam(required = false) @Nullable UUID profileId,
            @RequestParam(required = false) @Nullable String ageGroup,
            @RequestParam(required = false) @Nullable String factor,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(
                user.userId(),
                new VideoListQuery(
                        list,
                        profileId,
                        blankToNull(ageGroup) == null ? null : AgeGroup.fromLabel(ageGroup),
                        blankToNull(factor) == null
                                ? null
                                : FitnessFactor.fromLabel(factor).getLabel(),
                        blankToNull(cursor),
                        size));
    }

    @PostMapping("/{videoId}/favorite")
    public FavoriteView favorite(
            CurrentUser user, @PathVariable String videoId, @Valid @RequestBody FavoriteRequest body) {
        return service.favorite(
                user.userId(),
                videoId,
                new FavoriteCommand(
                        Objects.requireNonNull(body.profileId()), Objects.requireNonNull(body.favorited())));
    }

    @PostMapping("/{videoId}/progress")
    public VideoProgressView progress(
            CurrentUser user, @PathVariable String videoId, @Valid @RequestBody VideoProgressRequest body) {
        return service.progress(
                user.userId(),
                videoId,
                new VideoProgressCommand(
                        Objects.requireNonNull(body.profileId()),
                        Objects.requireNonNull(body.progress()),
                        Objects.requireNonNull(body.watchedSec()),
                        body.missionId()));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
