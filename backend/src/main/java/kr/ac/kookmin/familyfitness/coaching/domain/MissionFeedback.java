package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 참여자 한 명이 미션 하나에 남긴 느낌. 한 사람 · 한 미션에 한 줄이고 다시 보내면 덮어쓴다.
 *
 * @param createdAt 마지막으로 보낸 시각
 */
public record MissionFeedback(UUID missionId, UUID profileId, MissionFeel feel, Instant createdAt) {}
