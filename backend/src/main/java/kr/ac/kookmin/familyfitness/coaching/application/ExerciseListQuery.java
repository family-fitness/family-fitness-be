package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 운동 구간 목록 조건. 비어 있는 조건은 거르지 않는다.
 *
 * @param quiet true 면 조용한 구간만. false 면 가리지 않는다.
 * @param q 검색어. 앞뒤 공백을 뺀 값이고, 비었으면 null.
 * @param profileId 보는 프로필. 찜과 기본 나이대를 이 프로필로 정한다. 없으면 호출한 계정의 자기 프로필 나이대로 거르고 찜은 모두 false.
 * @param ageGroup 이 나이대 구간만 본다(어르신은 성인 구간도). null 이면 보는 프로필의 나이대다.
 * @param allAges true 면 나이대로 거르지 않는다(요청의 ageGroup=ALL). 이때 ageGroup 은 null 이다.
 * @param cursor 앞 쪽 마지막 구간의 clipId(앞 응답의 nextCursor). 첫 쪽이면 null.
 * @param size 한 쪽에 싣는 수. 1~{@link ExerciseService#MAX_SIZE}.
 */
public record ExerciseListQuery(
        @Nullable FitnessFactor factor,
        @Nullable SessionPhase phase,
        boolean quiet,
        @Nullable String q,
        ExerciseListType list,
        @Nullable UUID profileId,
        @Nullable AgeGroup ageGroup,
        boolean allAges,
        @Nullable String cursor,
        int size) {

    /** 나이대는 보는 프로필로, 첫 쪽, 기본 크기. */
    public ExerciseListQuery(
            @Nullable FitnessFactor factor,
            @Nullable SessionPhase phase,
            boolean quiet,
            @Nullable String q,
            ExerciseListType list,
            @Nullable UUID profileId) {
        this(factor, phase, quiet, q, list, profileId, null, false, null, ExerciseService.PAGE);
    }

    /**
     * 요인, 단계, 조용함, 검색어 조건(FE 목 src/mocks/clips.ts 와 같은 차례). 나이대와 찜은 서비스가 따로 건다.
     * 검색어는 목과 같이 화면에 내보내는 제목(title)의 부분 일치만 본다. 영상에 뜬 이름(nameOnVideo)은 보지 않는다.
     */
    boolean matches(ExerciseClip clip) {
        if (factor != null && clip.factor() != factor) return false;
        if (phase != null && clip.phase() != phase) return false;
        if (quiet && !clip.quiet()) return false;
        return q == null || clip.title().contains(q);
    }
}
