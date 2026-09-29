package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoMedia;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** 클립 표를 읽고, 공단 영상 클립에는 영상 표의 mp4 · 첫 장면 주소를 붙인다(클립 표 한 번 · 영상 표 한 번). */
@Repository
public class ExerciseClipPersistenceAdapter implements ExerciseClipRepository {
    private final ExerciseClipJpaRepository clips;
    private final ExerciseVideoJpaRepository videos;

    public ExerciseClipPersistenceAdapter(ExerciseClipJpaRepository clips, ExerciseVideoJpaRepository videos) {
        this.clips = clips;
        this.videos = videos;
    }

    @Override
    public List<ExerciseClip> findAllActive() {
        return withMedia(
                clips.findAllByActiveTrueOrderByVideoIdAscStartSecAsc(), mediaOf(videos.findAllByMediaUrlIsNotNull()));
    }

    @Override
    public @Nullable ExerciseClip findById(String clipId) {
        return clips.findById(clipId)
                .map(it -> it.toDomain(mediaOf(videos.findAllByVideoIdInAndMediaUrlIsNotNull(List.of(it.getVideoId())))
                        .getOrDefault(it.getVideoId(), VideoMedia.NONE)))
                .orElse(null);
    }

    @Override
    public List<ExerciseClip> findAllByIds(Collection<String> clipIds) {
        if (clipIds.isEmpty()) return List.of();
        List<ExerciseClipEntity> found = clips.findAllById(clipIds);
        if (found.isEmpty()) return List.of();
        List<String> videoIds =
                found.stream().map(ExerciseClipEntity::getVideoId).distinct().toList();
        return withMedia(found, mediaOf(videos.findAllByVideoIdInAndMediaUrlIsNotNull(videoIds)));
    }

    private static List<ExerciseClip> withMedia(List<ExerciseClipEntity> entities, Map<String, VideoMedia> media) {
        return entities.stream()
                .map(it -> it.toDomain(media.getOrDefault(it.getVideoId(), VideoMedia.NONE)))
                .toList();
    }

    private static Map<String, VideoMedia> mediaOf(List<ExerciseVideoEntity> mediaVideos) {
        return mediaVideos.stream()
                .collect(Collectors.toMap(ExerciseVideoEntity::getVideoId, ExerciseVideoEntity::media, (a, b) -> a));
    }
}
