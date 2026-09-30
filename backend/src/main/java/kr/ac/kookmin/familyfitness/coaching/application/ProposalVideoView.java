package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ProposalVideoView(
        String videoId,
        @Nullable String title,
        String url,
        @Nullable Integer startSec,
        List<String> badges) {}
