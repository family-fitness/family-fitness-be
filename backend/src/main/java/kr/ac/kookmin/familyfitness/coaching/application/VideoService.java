package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CursorPage;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 영상 목록·즐겨찾기·시청 진행률. 완주(≥0.9) 시 영상 길이만큼 활동 분을 한 번만 적립한다. */
@Service
public class VideoService {
    public static final int MAX_PAGE_SIZE = 100;

    private final ExerciseVideoRepository videos;
    private final VideoInteractionRepository interactions;
    private final MissionRepository missions;
    private final FamilyAccess familyAccess;
    private final ActivityRecorder recorder;
    private final MissionCompletionPolicy policy;
    private final AppTime time;

    public VideoService(
            ExerciseVideoRepository videos,
            VideoInteractionRepository interactions,
            MissionRepository missions,
            FamilyAccess familyAccess,
            ActivityRecorder recorder,
            MissionCompletionPolicy policy,
            AppTime time) {
        this.videos = videos;
        this.interactions = interactions;
        this.missions = missions;
        this.familyAccess = familyAccess;
        this.recorder = recorder;
        this.policy = policy;
        this.time = time;
    }

    /**
     * `ALL`·`FAVORITES` 는 videoId 오름차순 커서 페이지, `RECENT` 는 최근 시청순이며 커서를 무시한다.
     * `ageGroup` 은 안전 필터(라벨 없는 영상은 아이 연령대에 나가지 않음), `factor` 는 라벨 요인 포함 여부.
     */
    @Transactional(readOnly = true)
    public VideoListView list(UUID userId, VideoListQuery query) {
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size 는 1~" + MAX_PAGE_SIZE + " 이어야 합니다");
        }
        if (query.list() != VideoListType.ALL && query.profileId() == null) {
            throw new IllegalArgumentException(query.list() + " 목록에는 profileId 가 필요합니다");
        }
        if (query.profileId() != null) familyAccess.requireSameFamilyAsProfile(userId, query.profileId());
        Map<String, VideoInteraction> mine = new LinkedHashMap<>();
        if (query.profileId() != null) {
            interactions.findAllOf(query.profileId()).forEach(it -> mine.put(it.getVideoId(), it));
        }

        CursorPage<ExerciseVideo> page =
                switch (query.list()) {
                    case ALL ->
                        CursorPage.of(
                                videos.findAllAfter(query.cursor()).stream()
                                        .filter(it -> it.matches(query.ageGroup(), query.factor()))
                                        .toList(),
                                query.size(),
                                ExerciseVideo::getVideoId);
                    case FAVORITES -> {
                        List<String> ids = mine.values().stream()
                                .filter(VideoInteraction::isFavorited)
                                .map(VideoInteraction::getVideoId)
                                .filter(it -> query.cursor() == null || it.compareTo(query.cursor()) > 0)
                                .toList();
                        List<ExerciseVideo> sorted = videos.findAllByIds(ids).stream()
                                .filter(it -> it.matches(query.ageGroup(), query.factor()))
                                .sorted(Comparator.comparing(ExerciseVideo::getVideoId))
                                .toList();
                        yield CursorPage.of(sorted, query.size(), ExerciseVideo::getVideoId);
                    }
                    case RECENT -> {
                        List<VideoInteraction> recent = mine.values().stream()
                                .filter(VideoInteraction::isWatched)
                                .sorted(Comparator.comparing((VideoInteraction it) ->
                                                it.getLastWatchedAt() == null ? Instant.EPOCH : it.getLastWatchedAt())
                                        .reversed())
                                .toList();
                        Map<String, ExerciseVideo> byId =
                                videos
                                        .findAllByIds(recent.stream()
                                                .map(VideoInteraction::getVideoId)
                                                .toList())
                                        .stream()
                                        .collect(Collectors.toMap(
                                                ExerciseVideo::getVideoId, Function.identity(), (a, b) -> b));
                        List<ExerciseVideo> ordered = recent.stream()
                                .map(it -> byId.get(it.getVideoId()))
                                .filter(java.util.Objects::nonNull)
                                .filter(it -> it.matches(query.ageGroup(), query.factor()))
                                .toList();
                        yield new CursorPage<>(
                                ordered.stream().limit(query.size()).toList(), null);
                    }
                };
        List<VideoView> views = page.items().stream()
                .map(v -> {
                    VideoInteraction i = mine.get(v.getVideoId());
                    return new VideoView(
                            v.getVideoId(),
                            v.getTitle(),
                            v.getUrl(),
                            v.getThumbnailUrl(),
                            v.getDurationSec(),
                            v.getLabel(),
                            v.getBadges(),
                            i != null && i.isFavorited(),
                            i == null ? null : i.getMaxProgress());
                })
                .toList();
        return new VideoListView(views, page.nextCursor());
    }

    @Transactional
    public FavoriteView favorite(UUID userId, String videoId, FavoriteCommand command) {
        if (videos.findById(videoId) == null) throw new VideoNotFoundException(videoId);
        familyAccess.requireSameFamilyAsProfile(userId, command.profileId());
        Instant now = time.now();
        VideoInteraction interaction = interactions.find(command.profileId(), videoId);
        if (interaction == null) {
            interaction = VideoInteraction.start(UUID.randomUUID(), command.profileId(), videoId, now);
        }
        interaction.setFavorite(command.favorited(), now);
        interactions.save(interaction);
        return new FavoriteView(videoId, command.profileId(), interaction.isFavorited(), interaction.getFavoritedAt());
    }

    /**
     * 진행률 보고. 최대값만 남기고, 최초로 0.9 이상이 되면 `activity_daily`(VIDEO) 에 영상 길이(분, 올림)를 1회 적립한다.
     * missionId 가 있으면 그 미션의 참여자 진행도도 갱신한다.
     */
    @Transactional
    public VideoProgressView progress(UUID userId, String videoId, VideoProgressCommand command) {
        ExerciseVideo video = videos.findById(videoId);
        if (video == null) throw new VideoNotFoundException(videoId);
        familyAccess.requireSameFamilyAsProfile(userId, command.profileId());
        Instant now = time.now();
        VideoInteraction interaction = interactions.find(command.profileId(), videoId);
        if (interaction == null) {
            interaction = VideoInteraction.start(UUID.randomUUID(), command.profileId(), videoId, now);
        }
        interaction.watch(command.progress(), command.watchedSec(), now);

        int credited = 0;
        if (interaction.isCreditable()) {
            credited = video.getCreditMinutes();
            if (credited > 0) {
                recorder.addActiveMinutes(command.profileId(), time.today(), ActivitySource.VIDEO, credited);
            }
            interaction.markCredited(now);
        }
        interactions.save(interaction);

        Double missionProgress = null;
        UUID missionId = command.missionId();
        if (missionId != null) {
            Mission mission = missions.findById(missionId);
            if (mission == null) throw new MissionNotFoundException(missionId);
            mission.participantOf(command.profileId());
            missionProgress = policy.refreshParticipant(mission, command.profileId(), now)
                    .participantOf(command.profileId())
                    .getProgress();
        }
        return new VideoProgressView(
                interaction.getMaxProgress(),
                interaction.isCompleted(),
                credited,
                interaction.isCompleted() ? VerifiedBy.VIDEO_PROGRESS : null,
                missionProgress);
    }
}
