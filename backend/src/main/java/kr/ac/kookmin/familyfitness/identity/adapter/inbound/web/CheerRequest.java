package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** fromProfileId 는 내 프로필, 또는 내가 보호자일 때 같은 가족의 계정 없는 아이 프로필. */
public record CheerRequest(
        @NotNull @Nullable UUID fromProfileId,
        @NotNull @Nullable UUID toProfileId,
        @Size(max = 100) @Nullable String message,
        @Size(max = 20) @Nullable String emoji,
        @Nullable UUID missionId) {
    /** message/emoji 중 최소 하나. */
    @AssertTrue(message = "message 나 emoji 중 하나는 있어야 합니다")
    public boolean isContentPresent() {
        return !isNullOrBlank(message) || !isNullOrBlank(emoji);
    }

    private static boolean isNullOrBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
