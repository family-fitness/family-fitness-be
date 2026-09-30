package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel;
import org.jspecify.annotations.Nullable;

/**
 * 영상 한 편.
 *
 * @param url 공단 영상이면 mp4 주소, 유튜브 영상이면 보기 주소
 * @param thumbnailUrl 공단 영상이면 첫 장면 이미지, 유튜브 영상이면 유튜브 썸네일
 * @param mediaUrl 공단 영상의 mp4 주소. 유튜브 영상은 null
 */
public record VideoView(
        String videoId,
        String title,
        String url,
        String thumbnailUrl,
        @Nullable Integer durationSec,
        VideoLabel label,
        List<String> badges,
        boolean favorited,
        @Nullable Double maxProgress,
        @Nullable String mediaUrl) {}
