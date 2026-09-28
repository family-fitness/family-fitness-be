package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import org.jspecify.annotations.Nullable;

/** 보낸 응원. emoji 는 stickerId 와 같은 값이고 전환 기간에만 싣는다. */
public record CheerResponse(
        UUID cheerId,
        UUID fromProfileId,
        UUID toProfileId,
        CheerKind kind,
        @Nullable String message,
        @Nullable String stickerId,
        @Nullable String emoji,
        @Nullable UUID missionId,
        @Nullable UUID replyToCheerId,
        Instant createdAt) {
    public static CheerResponse of(Cheer cheer) {
        return new CheerResponse(
                cheer.id(),
                cheer.fromProfileId(),
                cheer.toProfileId(),
                cheer.kind(),
                cheer.message(),
                cheer.stickerId(),
                cheer.stickerId(),
                cheer.missionId(),
                cheer.replyToCheerId(),
                cheer.createdAt());
    }
}
