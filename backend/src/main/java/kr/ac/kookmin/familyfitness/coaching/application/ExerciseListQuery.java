package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 운동 구간 목록 조건. 비어 있는 조건은 거르지 않는다.
 *
 * @param quiet true 면 조용한 구간만. false 면 가리지 않는다.
 * @param q 검색어. 앞뒤 공백을 뺀 값이고, 비었으면 null.
 * @param profileId 보는 프로필. 연령대와 찜을 이 프로필로 정한다. 없으면 호출한 계정의 자기 프로필 연령대로 거르고 찜은 모두 false.
 */
public record ExerciseListQuery(
        @Nullable FitnessFactor factor,
        @Nullable SessionPhase phase,
        boolean quiet,
        @Nullable String q,
        ExerciseListType list,
        @Nullable UUID profileId) {

    /**
     * 요인 · 단계 · 조용함 · 검색어 조건(FE 목 src/mocks/clips.ts 와 같은 차례). 연령대 · 찜은 서비스가 따로 건다.
     * 검색어는 목과 같이 화면에 내보내는 제목(title)의 부분 일치만 본다. 영상에 뜬 이름(nameOnVideo)은 보지 않는다.
     */
    boolean matches(ExerciseClip clip) {
        if (factor != null && clip.factor() != factor) return false;
        if (phase != null && clip.phase() != phase) return false;
        if (quiet && !clip.quiet()) return false;
        return q == null || clip.title().contains(q);
    }
}
