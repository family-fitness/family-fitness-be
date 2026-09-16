package kr.ac.kookmin.familyfitness.shared.security;

import java.time.Instant;

/** 발급된 토큰 쌍. */
public record ServiceTokens(String accessToken, String refreshToken, Instant accessExpiresAt) {}
