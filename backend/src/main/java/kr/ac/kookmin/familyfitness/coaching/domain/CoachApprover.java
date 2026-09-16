package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;

/**
 * 승인·거절을 시도하는 사람. HTTP 요청의 role 값이 아니라 identity 조회 결과(부모 프로필)로 만든다.
 * {@code profileId} 는 승인하는 보호자의 프로필 id 이며 `coach_runs.approved_by` 에 그대로 남는다.
 */
public record CoachApprover(UUID profileId, UUID familyId, boolean isParent) {}
