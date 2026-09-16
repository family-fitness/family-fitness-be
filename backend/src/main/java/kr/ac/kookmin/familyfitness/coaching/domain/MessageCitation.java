package kr.ac.kookmin.familyfitness.coaching.domain;

import org.jspecify.annotations.Nullable;

/** 답변의 근거. {@code index} 는 AI 가 붙인 1부터 시작하는 번호이며 답변 본문의 `[n]` 과 맞물린다. */
public record MessageCitation(
        int index,
        String sourceLabel,
        @Nullable String chunkId,
        String excerpt,
        @Nullable String url) {}
