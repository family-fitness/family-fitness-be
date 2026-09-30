package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import org.jspecify.annotations.Nullable;

/**
 * 제안 칸의 영상 구간 제목(clip.title)을 찾는다. AI 는 칸에 제목을 싣지 않는다(exercise_name 은 칸 제목으로 쓴다).
 * 찾는 차례: 클립 표(video_exercises)에서 clipId = {videoId}-{startSec} 인 클립의 제목 → 영상 표(exercise_videos)의 영상 제목 → null.
 * 클립 표를 다시 적재해 시작 초가 달라졌으면 영상 제목으로 물러선다.
 */
@FunctionalInterface
public interface ClipTitles {
    @Nullable
    String titleOf(String videoId, int startSec);

    static ClipTitles none() {
        return (videoId, startSec) -> null;
    }

    /**
     * @param clipTitleById clipId → 클립 제목
     * @param videoTitleById videoId → 영상 제목
     */
    static ClipTitles of(Map<String, String> clipTitleById, Map<String, String> videoTitleById) {
        return (videoId, startSec) -> {
            String clipTitle = clipTitleById.get(ExerciseClip.idOf(videoId, startSec));
            return clipTitle != null ? clipTitle : videoTitleById.get(videoId);
        };
    }
}
