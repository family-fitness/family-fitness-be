package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.Collection;
import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import org.jspecify.annotations.Nullable;

public interface ExerciseVideoRepository {
    @Nullable
    ExerciseVideo findById(String videoId);

    List<ExerciseVideo> findAllByIds(Collection<String> videoIds);

    /** videoId 오름차순, {@code afterVideoId} 보다 큰 것부터. 카탈로그가 작아 필터는 메모리에서 한다(▲ 커지면 SQL 필터). */
    List<ExerciseVideo> findAllAfter(@Nullable String afterVideoId);
}
