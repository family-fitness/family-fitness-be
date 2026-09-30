package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoMedia;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 클립 표를 읽고, 공단 영상 클립에는 영상 표의 mp4 · 첫 장면 주소를 붙인다(클립 표 한 번 · 영상 표 한 번).
 * 켜진 클립 목록은 연령대 · 요인 · 단계 줄(video_exercise_labels)도 한 번 읽어, 줄이 있는 클립을 줄마다 후보 하나로 펼친다.
 */
@Repository
public class ExerciseClipPersistenceAdapter implements ExerciseClipRepository {
    private final ExerciseClipJpaRepository clips;
    private final ExerciseVideoJpaRepository videos;

    public ExerciseClipPersistenceAdapter(ExerciseClipJpaRepository clips, ExerciseVideoJpaRepository videos) {
        this.clips = clips;
        this.videos = videos;
    }

    /** 줄 하나. 공단 영상 표의 한 줄(연령대 · 요인 · 단계)이다. */
    private record Label(
            @Nullable AgeGroup ageGroup, @Nullable FitnessFactor factor, SessionPhase phase) {}

    @Override
    public List<ExerciseClip> findAllActive() {
        List<ExerciseClip> rows = withMedia(
                clips.findAllByActiveTrueOrderByVideoIdAscStartSecAsc(), mediaOf(videos.findAllByMediaUrlIsNotNull()));
        Map<String, List<Label>> labels = labelsByClip(clips.findActiveLabelRows());
        if (labels.isEmpty()) return rows;
        List<ExerciseClip> expanded = new ArrayList<>(rows.size() + labels.size());
        for (ExerciseClip clip : rows) {
            List<Label> mine = labels.get(clip.clipId());
            if (mine == null) {
                expanded.add(clip);
                continue;
            }
            for (Label label : mine) expanded.add(clip.withLabel(label.ageGroup(), label.factor(), label.phase()));
        }
        return List.copyOf(expanded);
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

    private static Map<String, List<Label>> labelsByClip(List<Object[]> rows) {
        Map<String, List<Label>> byClip = new HashMap<>();
        for (Object[] row : rows) {
            String factor = (String) row[2];
            byClip.computeIfAbsent((String) row[0], it -> new ArrayList<>())
                    .add(new Label(
                            AgeGroup.valueOf((String) row[1]),
                            factor == null ? null : FitnessFactor.valueOf(factor),
                            SessionPhase.valueOf((String) row[3])));
        }
        return byClip;
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
