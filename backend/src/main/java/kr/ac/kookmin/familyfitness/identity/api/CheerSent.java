package kr.ac.kookmin.familyfitness.identity.api;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 응원이 저장됐다는 도메인 이벤트. 저장과 같은 트랜잭션 안에서 발행한다.
 * 받는 쪽(캘린더 스티커 · 알림 · 경험치)은 커밋 뒤에 받도록 {@code @TransactionalEventListener} 나
 * {@code @ApplicationModuleListener} 로 듣는다.
 */
public record CheerSent(
        UUID cheerId,
        UUID familyId,
        UUID fromProfileId,
        UUID toProfileId,
        CheerKind kind,
        @Nullable String stickerId,
        @Nullable UUID missionId,
        @Nullable UUID replyToCheerId,
        Instant createdAt) {}
