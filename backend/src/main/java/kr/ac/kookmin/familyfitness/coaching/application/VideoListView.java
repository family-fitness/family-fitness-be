package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record VideoListView(
        List<VideoView> videos, @Nullable String nextCursor) {}
