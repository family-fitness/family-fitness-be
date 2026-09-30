package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 영상 안의 한 동작 구간(클립). AI 릴리스를 적재한 `video_exercises` 행이다. 읽기 전용.
 * 유튜브 영상은 한 편을 여러 동작 구간으로 자른 것(video_clips · clip_labels, V132)이고, 공단 「국민체력100 동영상 정보」 오픈API
 * 영상은 한 편에 운동 하나라 한 편이 곧 클립 하나다(kspo_videos, V161 ~ V165 — 시작 0초, 끝 = 영상 길이).
 * 공단 영상은 AI 표가 한 편을 연령대 · 요인 · 단계마다 한 줄로 준다(V165 video_exercise_labels). 켜진 클립 목록은 그 줄마다
 * {@link #withLabel} 로 연령대 · 요인 · 단계만 바꾼 값을 하나씩 낸다 — 「공통」 영상은 청소년 · 성인 두 번 나온다.
 *
 * <p>{@code clipId} 는 {@code {videoId}-{startSec}} 다. AI 의 {@code seq} 는 영상 안 순번이라 클립을 다시 끊으면 밀리지만,
 * 시작 초는 다시 끊어도 운동 클립이 그대로 남았다(9/17 → 9/22 판에서 491/491).
 *
 * @param nameOnVideo 영상 화면에 뜬 이름(OCR). 라벨 조인 키다.
 * @param exerciseName 처방 어휘로 옮긴 이름. 못 옮긴 클립은 null.
 * @param title 화면에 내보낼 이름. {@code exerciseName} 이 있으면 그것, 없으면 {@code nameOnVideo}.
 * @param factor 라벨의 체력 요인. 라벨이 비었으면 null.
 * @param isExercise 운동 동작인지. 휴식 · 인사 체조 · 숨쉬기 같은 구간은 false 로 두고 행은 남긴다.
 * @param ageGroup 영상의 연령대(유튜브는 AI 코퍼스 값, 공단은 AI 표의 그 줄 값). 모르면 null.
 * @param source 유튜브는 라벨을 붙인 방법(AI 값 그대로: llm · human · exact · embed), 공단은 AI 표 값(kspo, V165).
 * @param active 지금 AI 판에 있는 클립인지. 새 판에서 빠진 클립은 지우지 않고 false 로 둔다(찜처럼 clipId 를 가리키는 행을 살린다).
 * @param media 공단 영상이면 mp4 · 첫 장면 주소. 유튜브 클립은 {@link VideoMedia#NONE}(videoId 로 유튜브 구간을 튼다).
 */
public record ExerciseClip(
        String clipId,
        String videoId,
        int seq,
        String nameOnVideo,
        @Nullable String exerciseName,
        String title,
        @Nullable FitnessFactor factor,
        SessionPhase phase,
        int startSec,
        int endSec,
        boolean homeOk,
        boolean quiet,
        boolean needsProps,
        boolean isExercise,
        @Nullable AgeGroup ageGroup,
        @Nullable String source,
        boolean active,
        VideoMedia media) {

    /** 유튜브 클립. */
    public ExerciseClip(
            String clipId,
            String videoId,
            int seq,
            String nameOnVideo,
            @Nullable String exerciseName,
            String title,
            @Nullable FitnessFactor factor,
            SessionPhase phase,
            int startSec,
            int endSec,
            boolean homeOk,
            boolean quiet,
            boolean needsProps,
            boolean isExercise,
            @Nullable AgeGroup ageGroup,
            @Nullable String source,
            boolean active) {
        this(
                clipId,
                videoId,
                seq,
                nameOnVideo,
                exerciseName,
                title,
                factor,
                phase,
                startSec,
                endSec,
                homeOk,
                quiet,
                needsProps,
                isExercise,
                ageGroup,
                source,
                active,
                VideoMedia.NONE);
    }

    /**
     * 연령대 · 요인 · 단계만 바꾼 같은 클립. 공단 영상은 AI 표가 한 편을 연령대 · 요인 · 단계마다 한 줄로 주고(V165
     * video_exercise_labels), 클립 목록은 그 줄마다 이 값으로 후보 하나를 낸다(AI catalog.py 가 줄마다 Clip 하나를 만드는 것과 같다).
     */
    public ExerciseClip withLabel(@Nullable AgeGroup ageGroup, @Nullable FitnessFactor factor, SessionPhase phase) {
        return new ExerciseClip(
                clipId,
                videoId,
                seq,
                nameOnVideo,
                exerciseName,
                title,
                factor,
                phase,
                startSec,
                endSec,
                homeOk,
                quiet,
                needsProps,
                isExercise,
                ageGroup,
                source,
                active,
                media);
    }

    public static String idOf(String videoId, int startSec) {
        return videoId + "-" + startSec;
    }

    /**
     * 이 연령대(viewer)에게 보여 줄 구간인지. 연령대가 같으면 된다. 어르신은 성인 구간도 받는다 — 노인 전용 영상을 따로 만들지 않고
     * 성인 영상을 똑같이 쓰기로 했다(유튜브 어르신 구간은 V132 에 0개, 공단 어르신 영상은 V161 이 더했다가 V164 가 껐다 — 지금 켜진 어르신
     * 구간은 없다). 성인은 어르신 구간을, 어르신은 청소년 구간을 받지 않는다. 「공통」 공단 영상은 청소년 줄 · 성인 줄이 따로 있어(V165)
     * 청소년 · 성인 · 어르신 모두 받는다.
     */
    public boolean suits(AgeGroup viewer) {
        return ageGroup == viewer || (viewer == AgeGroup.SENIOR && ageGroup == AgeGroup.ADULT);
    }
}
