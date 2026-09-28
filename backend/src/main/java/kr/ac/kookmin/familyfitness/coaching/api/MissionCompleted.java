package kr.ac.kookmin.familyfitness.coaching.api;

import java.time.Instant;
import java.util.UUID;

/** 한 사람이 미션을 끝까지 했다 — 칸 끝으로 그 사람의 참여자 상태가 완료로 바뀐 순간 한 번(결정 42). */
public record MissionCompleted(UUID missionId, UUID profileId, UUID familyId, Instant completedAt) {}
