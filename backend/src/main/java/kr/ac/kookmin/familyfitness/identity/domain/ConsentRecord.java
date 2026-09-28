package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 프로필의 지금 동의 상태. 철회해도 과거 동의 시각은 지우지 않고 {@code revokedAt} 만 채운다.
 * 유효한 동의 = 두 시각이 있고 철회되지 않음. 재동의는 이 상태의 {@code revokedAt} 을 걷지만,
 * 부여 · 철회 한 번 한 번은 {@link ConsentEvent} 로 consent_events 이력에 따로 남는다(누가 · 언제).
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

    /** 거둔 채인가. 나이와 상관없이 다시 동의할 때까지 막는다({@link Profile#consentRequired}). */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    public ConsentRecord revoke(Instant at) {
        return new ConsentRecord(personalAt, healthAt, byUserId, at);
    }

    public static ConsentRecord granted(Instant at, UUID byUserId) {
        return new ConsentRecord(at, at, byUserId, null);
    }
}
