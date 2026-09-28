package kr.ac.kookmin.familyfitness.coaching.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 오늘 서는 미션 하나와 그 미션을 아직 끝내지 않은 참여자.
 *
 * @param createdAt 미션을 만든 시각(승인한 제안이면 승인한 시각)
 * @param pendingProfileIds 아직 끝내지 않은 참여자(역할을 가르지 않는다). 비어 있지 않다
 */
public record StandingMission(
        UUID missionId, UUID familyId, String title, Instant createdAt, List<UUID> pendingProfileIds) {
    public StandingMission {
        pendingProfileIds = List.copyOf(pendingProfileIds);
    }
}
