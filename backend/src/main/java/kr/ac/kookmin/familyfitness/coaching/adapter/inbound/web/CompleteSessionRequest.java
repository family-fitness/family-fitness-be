package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 운동 한 칸 끝 몸통(fe:src/lib/api/queries.ts 칸 끝 — {profileId, activeSeconds, startedAt, endedAt}).
 *
 * @param activeSeconds 영상 재생 시간(초). 상한은 타이머 기록(RecordTimerRequest)과 같은 180분이다
 */
public record CompleteSessionRequest(
        @NotNull @Nullable UUID profileId,
        @NotNull @PositiveOrZero @Max(10_800) @Nullable Integer activeSeconds,
        @NotNull @Nullable Instant startedAt,
        @NotNull @Nullable Instant endedAt) {}
