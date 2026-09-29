package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.Collection;
import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import org.jspecify.annotations.Nullable;

public interface ExerciseVideoRepository {
    @Nullable
    ExerciseVideo findById(String videoId);

    List<ExerciseVideo> findAllByIds(Collection<String> videoIds);

    /**
     * 목록에 세울 영상, videoId 오름차순, {@code afterVideoId} 보다 큰 것부터. 카탈로그가 작아 연령 · 요인 필터는 메모리에서 한다(▲ 커지면 SQL 필터).
     * 새 판에서 빠져 클립이 모두 꺼진 공단 영상은 없다(V162 가 끈 근골격계운동 114편 등). 그런 영상도 {@link #findById} ·
     * {@link #findAllByIds} 로는 찾힌다 — 지난 미션 · 시청 기록 · 즐겨찾기가 가리키기 때문이다.
     */
    List<ExerciseVideo> findAllAfter(@Nullable String afterVideoId);
}
