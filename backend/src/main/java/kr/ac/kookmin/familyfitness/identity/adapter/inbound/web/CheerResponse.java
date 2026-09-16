package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import org.jspecify.annotations.Nullable;

public record CheerResponse(
        UUID cheerId,
        UUID fromProfileId,
        UUID toProfileId,
        @Nullable String message,
        @Nullable String emoji,
        @Nullable UUID missionId,
        Instant createdAt) {
    public static CheerResponse of(Cheer cheer) {
        return new CheerResponse(
                cheer.id(),
                cheer.fromProfileId(),
                cheer.toProfileId(),
                cheer.message(),
                cheer.emoji(),
                cheer.missionId(),
                cheer.createdAt());
    }
}
