package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** JPQL 참여 행 한 줄 — {@link MissionSpanRow} 에 누구의 줄인지(profileId) · 어느 미션인지(missionId)와 미션을 만든 시각을 더했다. */
public record ParticipantSpanRow(
        UUID profileId,
        UUID missionId,
        LocalDate startsOn,
        LocalDate endsOn,
        String targetMetric,
        String status,
        BigDecimal progress,
        @Nullable Instant verifiedAt,
        Instant missionCreatedAt) {}
