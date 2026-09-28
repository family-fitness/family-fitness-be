package kr.ac.kookmin.familyfitness.progress.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 업적 한 개. title · description 은 서버가 정한 문구다. earnedAt 은 처음 받은 시각, 아직이면 null. */
public record AchievementView(
        String code,
        String title,
        String description,
        @Nullable Instant earnedAt) {}
