package kr.ac.kookmin.familyfitness.shared.security;

import java.util.UUID;

/** 서명 · 용도 · 발급자 · 만료를 통과한 리프레시 토큰의 계정(sub)과 jti. */
public record RefreshTokenClaims(UUID userId, UUID tokenId) {}
