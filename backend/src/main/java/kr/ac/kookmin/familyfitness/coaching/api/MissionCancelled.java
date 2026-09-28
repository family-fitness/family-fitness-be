package kr.ac.kookmin.familyfitness.coaching.api;

import java.time.Instant;
import java.util.UUID;

/** 보호자가 미션을 지웠다. 지우는 트랜잭션 안에서 발행한다. */
public record MissionCancelled(UUID missionId, UUID familyId, Instant cancelledAt) {}
