package kr.ac.kookmin.familyfitness.shared.ai;

import org.jspecify.annotations.Nullable;

public record Citation(
        int index, String label, String chunkId, @Nullable String url) {}
