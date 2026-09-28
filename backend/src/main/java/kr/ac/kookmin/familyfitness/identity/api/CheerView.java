package kr.ac.kookmin.familyfitness.identity.api;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 응원 한 건을 읽는 모양. 받은 응원 목록(GET cheers)과 다른 모듈(캘린더 · 경험치)이 같이 쓴다.
 * fromName 은 보낸 프로필의 표시 이름이다. replyToCheerId 는 THANKS 가 답한 스티커의 cheerId 다.
 */
public record CheerView(
        UUID cheerId,
        UUID fromProfileId,
        String fromName,
        UUID toProfileId,
        CheerKind kind,
        @Nullable String message,
        @Nullable String stickerId,
        @Nullable UUID missionId,
        @Nullable UUID replyToCheerId,
        Instant createdAt) {}
