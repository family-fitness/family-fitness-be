package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** JPQL 참여 행 한 줄 — 미션 기간 · 지표와 그 참여자의 상태 · 진행도 · 완료 시각. */
public record MissionSpanRow(
        UUID missionId,
        LocalDate startsOn,
        LocalDate endsOn,
        String targetMetric,
        String status,
        BigDecimal progress,
        @Nullable Instant verifiedAt) {}
