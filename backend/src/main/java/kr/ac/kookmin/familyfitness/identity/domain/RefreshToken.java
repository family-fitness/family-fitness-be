package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 발급한 리프레시 토큰 한 개의 기록. 토큰 문자열은 두지 않고 jti({@code id})만 둔다.
 * 한 번 로그인해서 이어지는 회전은 모두 같은 {@code familyId} 를 가진다. 회전되면 {@code revokedAt} 과
 * 다음 토큰의 jti({@code replacedBy})가 채워지고, 로그아웃 · 재사용 감지로 폐기되면 {@code revokedAt} 만 채워진다.
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        UUID familyId,
        Instant createdAt,
        Instant expiresAt,
        @Nullable Instant revokedAt,
        @Nullable UUID replacedBy) {
    public static RefreshToken issued(UUID id, UUID userId, UUID familyId, Instant createdAt, Instant expiresAt) {
        return new RefreshToken(id, userId, familyId, createdAt, expiresAt, null, null);
    }

    public boolean revoked() {
        return revokedAt != null;
    }
}
