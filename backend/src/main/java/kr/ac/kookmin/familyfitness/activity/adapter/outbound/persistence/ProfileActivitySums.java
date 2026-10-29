package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 프로필별 JPQL 합계 결과(시간은 초). {@link ActivitySums} 에 프로필 칸을 더한 것이다. */
public record ProfileActivitySums(
        UUID profileId,
        @Nullable Long steps,
        @Nullable Long activeSeconds,
        @Nullable Long verifiedSeconds) {}
