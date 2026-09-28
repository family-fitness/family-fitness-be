package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 영상 안의 한 동작 구간(클립). AI 릴리스(video_clips · clip_labels)를 적재한 `video_exercises` 행이다. 읽기 전용.
 *
 * <p>{@code clipId} 는 {@code {videoId}-{startSec}} 다. AI 의 {@code seq} 는 영상 안 순번이라 클립을 다시 끊으면 밀리지만,
 * 시작 초는 다시 끊어도 운동 클립이 그대로 남았다(9/17 → 9/22 판에서 491/491).
 *
 * @param nameOnVideo 영상 화면에 뜬 이름(OCR). 라벨 조인 키다.
 * @param exerciseName 처방 어휘로 옮긴 이름. 못 옮긴 클립은 null.
 * @param title 화면에 내보낼 이름. {@code exerciseName} 이 있으면 그것, 없으면 {@code nameOnVideo}.
 * @param factor 라벨의 체력 요인. 라벨이 비었으면 null.
 * @param isExercise 운동 동작인지. 휴식 · 인사 체조 · 숨쉬기 같은 구간은 false 로 두고 행은 남긴다.
 * @param ageGroup 영상의 연령대(AI 코퍼스 값). 모르면 null.
 * @param source 라벨을 붙인 방법(AI 값 그대로: llm · human · exact · embed).
 * @param active 지금 AI 판에 있는 클립인지. 새 판에서 빠진 클립은 지우지 않고 false 로 둔다(찜처럼 clipId 를 가리키는 행을 살린다).
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
        boolean active) {

    public static String idOf(String videoId, int startSec) {
        return videoId + "-" + startSec;
    }
}
