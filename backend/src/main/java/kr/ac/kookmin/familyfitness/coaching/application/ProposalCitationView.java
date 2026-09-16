package kr.ac.kookmin.familyfitness.coaching.application;

import org.jspecify.annotations.Nullable;

public record ProposalCitationView(
        int index, String label, String chunkId, @Nullable String url) {}
