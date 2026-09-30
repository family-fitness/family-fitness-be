package kr.ac.kookmin.familyfitness.coaching.application;

import org.jspecify.annotations.Nullable;

/**
 * 미션의 대표 영상.
 *
 * @param url 공단 영상이면 mp4 주소, 유튜브 영상이면 보기 주소
 * @param mediaUrl 공단 영상의 mp4 주소. 유튜브 영상은 null
 * @param thumbnailUrl 공단 영상의 첫 장면 이미지. 유튜브 영상은 null
 */
public record MissionVideoView(
        String videoId,
        @Nullable String title,
        String url,
        @Nullable Integer durationSec,
        @Nullable Integer startSec,
        @Nullable String mediaUrl,
        @Nullable String thumbnailUrl) {}
