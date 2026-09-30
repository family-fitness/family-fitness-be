package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoMedia;
import org.jspecify.annotations.Nullable;

/**
 * 칸의 영상 구간. {@code endSec} 이 없으면 구간이 아니라 영상 한 편이다.
 *
 * @param mediaUrl 공단 영상이면 mp4 주소(화면은 이 주소를 &lt;video&gt; 로 튼다). 유튜브 영상은 null(videoId 로 유튜브를 튼다)
 * @param thumbnailUrl 공단 영상의 첫 장면 이미지. 유튜브 영상은 null
 */
public record SessionClipView(
        String videoId,
        int startSec,
        @Nullable Integer endSec,
        @Nullable String title,
        @Nullable String mediaUrl,
        @Nullable String thumbnailUrl) {

    /** 유튜브 구간. */
    public SessionClipView(String videoId, int startSec, @Nullable Integer endSec, @Nullable String title) {
        this(videoId, startSec, endSec, title, null, null);
    }

    static SessionClipView of(SessionClip clip, VideoMedia media) {
        return new SessionClipView(
                clip.videoId(), clip.startSec(), clip.endSec(), clip.title(), media.mediaUrl(), media.thumbnailUrl());
    }
}
