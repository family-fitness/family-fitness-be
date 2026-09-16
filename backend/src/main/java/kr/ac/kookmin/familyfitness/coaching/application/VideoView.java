package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel;
import org.jspecify.annotations.Nullable;

public record VideoView(
        String videoId,
        String title,
        String url,
        String thumbnailUrl,
        @Nullable Integer durationSec,
        VideoLabel label,
        List<String> badges,
        boolean favorited,
        @Nullable Double maxProgress) {}
