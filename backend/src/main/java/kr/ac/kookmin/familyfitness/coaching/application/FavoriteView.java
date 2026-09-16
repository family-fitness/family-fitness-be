package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FavoriteView(
        String videoId,
        UUID profileId,
        boolean favorited,
        @Nullable Instant favoritedAt) {}
