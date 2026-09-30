package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.Collection;
import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import org.jspecify.annotations.Nullable;

/** AI 클립 카탈로그. 적재 마이그레이션이 채우고 이 모듈은 읽기만 한다. */
public interface ExerciseClipRepository {
    /**
     * 지금 AI 판에 있는 클립(active)만, 영상 id · 시작 초 오름차순. 운동이 아닌 클립(isExercise=false)도 담는다 — 거르는 것은
     * 부르는 쪽이 한다. 공단 영상 클립은 AI 표의 연령대 · 요인 · 단계 줄마다 하나씩 나온다(V165) — 「공통」 영상은 청소년 · 성인
     * 두 번, 준비 · 정리 둘인 스트레칭은 두 단계로. 그래서 같은 clipId 가 여러 번 나올 수 있다(AI catalog.py 가 줄마다 후보를 두는 것과
     * 같다). 부르는 쪽은 제목(운동 찾기)이나 이름(대체 편성)으로 한 번만 고른다.
     */
    List<ExerciseClip> findAllActive();

    /** 새 표에서 빠진 클립(active=false)도 돌려준다. 찜처럼 예전에 저장한 clipId 를 풀 때 쓴다. 공단 클립은 클립 행(첫 줄 값) 하나다. */
    @Nullable
    ExerciseClip findById(String clipId);

    /** {@link #findById} 의 여러 건 판(한 번에 조회). 없는 id 는 건너뛴다. 차례는 정하지 않는다. */
    List<ExerciseClip> findAllByIds(Collection<String> clipIds);
}
