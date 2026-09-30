package kr.ac.kookmin.familyfitness.coaching.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 미션이 새로 생겼다(직접 만들기 · 여러 날 만들기 · 제안 승인). 미션마다 하나씩, 만든 트랜잭션 안에서 발행한다.
 *
 * @param participantProfileIds 참여자 전원(역할을 가르지 않는다)
 */
public record MissionCreated(
        UUID missionId,
        UUID familyId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        List<UUID> participantProfileIds,
        Instant createdAt) {}
