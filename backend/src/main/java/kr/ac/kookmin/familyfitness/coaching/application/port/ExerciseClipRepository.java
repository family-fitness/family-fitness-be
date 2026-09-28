package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.Collection;
import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import org.jspecify.annotations.Nullable;

/** AI 클립 카탈로그. 적재 마이그레이션이 채우고 이 모듈은 읽기만 한다. */
public interface ExerciseClipRepository {
    /**
     * 지금 AI 판에 있는 클립(active)만, 영상 id · 시작 초 오름차순. 운동이 아닌 클립(isExercise=false)도 담는다 — 거르는 것은
     * 부르는 쪽이 한다.
     */
    List<ExerciseClip> findAllActive();

    /** 새 판에서 빠진 클립(active=false)도 돌려준다. 찜처럼 예전에 저장한 clipId 를 풀 때 쓴다. */
    @Nullable
    ExerciseClip findById(String clipId);

    /** {@link #findById} 의 여러 건 판(한 번에 조회). 없는 id 는 건너뛴다. 차례는 정하지 않는다. */
    List<ExerciseClip> findAllByIds(Collection<String> clipIds);
}
