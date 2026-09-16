package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record VideoSearchResponse(List<Hit> hits, int filteredOutAgeGroup, int filteredOutBelowThreshold) {
    public record Hit(
            String videoId,
            @Nullable Integer startSec,
            double score,
            List<String> matchedExerciseNames,
            Citation citation) {}
}
