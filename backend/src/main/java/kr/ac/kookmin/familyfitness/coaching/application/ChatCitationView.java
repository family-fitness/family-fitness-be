package kr.ac.kookmin.familyfitness.coaching.application;

import org.jspecify.annotations.Nullable;

public record ChatCitationView(
        int index,
        String sourceLabel,
        String excerpt,
        @Nullable String url) {}
