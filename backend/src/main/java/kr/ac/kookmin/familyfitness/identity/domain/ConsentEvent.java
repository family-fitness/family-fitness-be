package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 보호자 동의를 바꾼 한 번의 기록(consent_events 한 줄). 넣기만 하고 고치거나 지우지 않는다.
 * 재동의가 {@link ConsentRecord} 의 철회 시각을 걷어도 철회한 줄은 여기 남는다.
 * {@code personalData} · {@code healthData} 는 보호자가 보낸 값 그대로다(부여면 둘 다 true).
 */
public record ConsentEvent(
        UUID profileId, UUID actorUserId, Kind kind, boolean personalData, boolean healthData, Instant occurredAt) {
    public enum Kind {
        GRANTED,
        REVOKED
    }

    static ConsentEvent of(UUID profileId, UUID actorUserId, GuardianConsent decision, Instant at) {
        return new ConsentEvent(
                profileId,
                actorUserId,
                decision.isComplete() ? Kind.GRANTED : Kind.REVOKED,
                decision.personalData(),
                decision.healthData(),
                at);
    }
}
