package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 운동 구간 하나. 칸 이름과 모양은 FE 의 ClipView(src/lib/api/types.ts)와 같다.
 *
 * @param clipId {@code {videoId}-{startSec}}. 찜 · 직접 짜기가 이 값으로 구간을 가리킨다.
 * @param factor 한글 요인 이름(예: 유연성). 라벨이 비었으면 null.
 * @param props 준비물이 있어야 하는 구간인가(needs_props).
 * @param favorited 보는 프로필이 이 구간을 찜했는가. profileId 없이 부르면 false.
 * @param mediaUrl 공단 영상이면 mp4 주소(한 편 = 구간 하나, startSec 0 ~ endSec 영상 길이). 유튜브 구간은 null
 * @param thumbnailUrl 공단 영상의 첫 장면 이미지. 유튜브 구간은 null
 */
public record ExerciseView(
        String clipId,
        String videoId,
        int startSec,
        int endSec,
        String title,
        @Nullable FitnessFactor factor,
        SessionPhase phase,
        boolean homeOk,
        boolean quiet,
        boolean props,
        boolean favorited,
        @Nullable String mediaUrl,
        @Nullable String thumbnailUrl) {

    static ExerciseView of(ExerciseClip clip, boolean favorited) {
        return new ExerciseView(
                clip.clipId(),
                clip.videoId(),
                clip.startSec(),
                clip.endSec(),
                clip.title(),
                clip.factor(),
                clip.phase(),
                clip.homeOk(),
                clip.quiet(),
                clip.needsProps(),
                favorited,
                clip.media().mediaUrl(),
                clip.media().thumbnailUrl());
    }
}
