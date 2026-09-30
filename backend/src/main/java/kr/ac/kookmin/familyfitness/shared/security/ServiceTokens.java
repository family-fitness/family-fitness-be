package kr.ac.kookmin.familyfitness.shared.security;

import java.time.Instant;
import java.util.UUID;

/**
 * 발급된 토큰 쌍. {@code refreshTokenId} 는 리프레시 토큰의 jti 다. identity 가 이 값으로 발급 기록을 남기고
 * 회전 · 폐기를 판단한다.
 */
public record ServiceTokens(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        UUID refreshTokenId,
        Instant refreshExpiresAt) {}
