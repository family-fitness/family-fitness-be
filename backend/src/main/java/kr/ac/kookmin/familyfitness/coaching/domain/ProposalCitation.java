package kr.ac.kookmin.familyfitness.coaching.domain;

import org.jspecify.annotations.Nullable;

public record ProposalCitation(
        int index, String label, String chunkId, @Nullable String url) {}
