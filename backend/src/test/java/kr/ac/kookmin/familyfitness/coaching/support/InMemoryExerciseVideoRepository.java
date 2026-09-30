package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import org.jspecify.annotations.Nullable;

public class InMemoryExerciseVideoRepository implements ExerciseVideoRepository {
    public final Map<String, ExerciseVideo> videos = new LinkedHashMap<>();

    public InMemoryExerciseVideoRepository() {
        this(List.of());
    }

    public InMemoryExerciseVideoRepository(List<ExerciseVideo> videos) {
        videos.forEach(it -> this.videos.put(it.getVideoId(), it));
    }

    @Override
    public @Nullable ExerciseVideo findById(String videoId) {
        return videos.get(videoId);
    }

    @Override
    public List<ExerciseVideo> findAllByIds(Collection<String> videoIds) {
        List<ExerciseVideo> found = new ArrayList<>();
        for (String videoId : videoIds) {
            ExerciseVideo video = videos.get(videoId);
            if (video != null) found.add(video);
        }
        return List.copyOf(found);
    }

    @Override
    public List<ExerciseVideo> findAllAfter(@Nullable String afterVideoId) {
        return videos.values().stream()
                .filter(it -> afterVideoId == null || it.getVideoId().compareTo(afterVideoId) > 0)
                .sorted(Comparator.comparing(ExerciseVideo::getVideoId))
                .toList();
    }
}
