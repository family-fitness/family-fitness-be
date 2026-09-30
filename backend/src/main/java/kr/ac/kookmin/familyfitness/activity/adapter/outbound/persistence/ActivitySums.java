package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import org.jspecify.annotations.Nullable;

/** JPQL 합계 결과(시간은 초). 행이 없으면 sum 이 null 이라 nullable 로 받는다. */
public record ActivitySums(
        @Nullable Long steps,
        @Nullable Long activeSeconds,
        @Nullable Long verifiedSeconds) {}
