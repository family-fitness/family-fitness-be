package kr.ac.kookmin.familyfitness.coaching.application;

import org.jspecify.annotations.Nullable;

public record MissionVideoView(
        String videoId,
        @Nullable String title,
        String url,
        @Nullable Integer durationSec,
        @Nullable Integer startSec) {}
