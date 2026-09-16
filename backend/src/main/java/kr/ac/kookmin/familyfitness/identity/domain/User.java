package kr.ac.kookmin.familyfitness.identity.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 계정. OAuth 제공자의 식별자(provider, providerUserId)로만 연결하며 사람(Profile)과 다르다.
 * 이메일이 같아도 제공자 식별자가 다르면 별도 계정이다 — 계정 병합 경로는 없다.
 */
public record User(
        UUID id,
        String provider,
        String providerUserId,
        @Nullable String email,
        UserStatus status) {
    public static final String PROVIDER_GOOGLE = "GOOGLE";
    public static final String PROVIDER_DEV = "DEV";

    public User {
        if (provider.isBlank()) throw new IllegalArgumentException("provider 는 비어 있을 수 없다");
        if (providerUserId.isBlank()) throw new IllegalArgumentException("providerUserId 는 비어 있을 수 없다");
    }

    public User(UUID id, String provider, String providerUserId, @Nullable String email) {
        this(id, provider, providerUserId, email, UserStatus.ACTIVE);
    }

    public static User register(String provider, String providerUserId, @Nullable String email) {
        return new User(UUID.randomUUID(), provider, providerUserId, email);
    }
}
