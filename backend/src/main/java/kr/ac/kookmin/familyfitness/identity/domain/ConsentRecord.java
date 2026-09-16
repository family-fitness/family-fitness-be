package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 프로필에 기록된 동의. 철회해도 과거 동의 시각은 지우지 않고 {@code revokedAt} 만 채운다(감사 목적).
 * 유효한 동의 = 두 시각이 있고 철회되지 않음.
 */
public record ConsentRecord(
        @Nullable Instant personalAt,
        @Nullable Instant healthAt,
        @Nullable UUID byUserId,
        @Nullable Instant revokedAt) {
    public static final ConsentRecord NONE = new ConsentRecord(null, null, null, null);

    public boolean isGiven() {
        return personalAt != null && healthAt != null && revokedAt == null;
    }

    public ConsentRecord revoke(Instant at) {
        return new ConsentRecord(personalAt, healthAt, byUserId, at);
    }

    public static ConsentRecord granted(Instant at, UUID byUserId) {
        return new ConsentRecord(at, at, byUserId, null);
    }
}
