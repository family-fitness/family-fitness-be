package kr.ac.kookmin.familyfitness.shared.security;

import java.util.UUID;

/**
 * 인증된 계정. 컨트롤러 파라미터로 선언하면 JWT `sub` 에서 채워진다.
 * actor(계정)와 대상 profileId 는 다르다 — 부모가 아이 기록을 대리 입력한다.
 */
public record CurrentUser(UUID userId) {}
