package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record CoachMessageResponse(
        String answer,
        List<Citation> citations,
        boolean refused,
        /* no_relevant_source · age_filter_empty · medical_query · no_citation_generated */
        @Nullable String refusalReason) {}
