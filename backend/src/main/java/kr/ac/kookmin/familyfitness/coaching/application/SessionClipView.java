package kr.ac.kookmin.familyfitness.coaching.application;

import org.jspecify.annotations.Nullable;

/** 칸의 영상 구간. {@code endSec} 이 없으면 구간이 아니라 영상 한 편이다. */
public record SessionClipView(
        String videoId,
        int startSec,
        @Nullable Integer endSec,
        @Nullable String title) {}
