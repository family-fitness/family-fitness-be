package kr.ac.kookmin.familyfitness.identity.api;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 응원이 저장됐다는 도메인 이벤트. 저장과 같은 트랜잭션 안에서 발행한다.
 * 경험치(progress)는 같은 트랜잭션에서 동기로 듣는다 — 이벤트 저장소가 없어 커밋 뒤에 받다 실패하면 적립이 조용히 빠지고,
 * FE 는 스티커를 붙인 곧바로 레벨을 다시 읽는다. 알림처럼 응원 저장을 막으면 안 되는 쪽은
 * 커밋 뒤에 받도록 {@code @TransactionalEventListener} 나 {@code @ApplicationModuleListener} 로 듣는다.
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
