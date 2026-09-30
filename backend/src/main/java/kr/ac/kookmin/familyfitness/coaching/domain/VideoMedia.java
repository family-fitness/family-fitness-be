package kr.ac.kookmin.familyfitness.coaching.domain;

import org.jspecify.annotations.Nullable;

/**
 * 유튜브가 아닌 영상을 트는 주소. 공단 「국민체력100 동영상 정보」 오픈API 영상만 두 칸이 차 있고, 유튜브 영상은 둘 다 null 이다
 * ({@link #NONE}). 화면은 mediaUrl 이 있으면 mp4 로, 없으면 videoId 로 유튜브를 튼다.
 *
 * @param mediaUrl mp4 주소(https)
 * @param thumbnailUrl 첫 장면 이미지(https)
 */
public record VideoMedia(
        @Nullable String mediaUrl, @Nullable String thumbnailUrl) {
    public static final VideoMedia NONE = new VideoMedia(null, null);

    /** 영상 표에서 찾은 영상의 주소. 표에 없는 영상(null)은 유튜브로 본다. */
    public static VideoMedia of(@Nullable ExerciseVideo video) {
        return video == null ? NONE : video.getMedia();
    }
}
