package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import org.jspecify.annotations.Nullable;

/**
 * fromProfileId 는 내 프로필, 또는 내가 보호자일 때 같은 가족의 계정 없는 아이 프로필.
 * stickerId 는 FE 스티커 코드다. emoji 는 stickerId 의 옛 이름이라 전환 기간에만 받고, 둘 다 오면 stickerId 를 쓴다.
 * kind 가 없으면 서버가 정한다 — 부모가 보내거나 아이가 받으면 PRAISE, 아이 → 부모는 스티커가 있으면 THANKS · 없으면 DONE.
 * replyToCheerId 는 THANKS 가 답하는 칭찬 스티커의 cheerId 이고 THANKS 에만 보낸다.
 */
public record CheerRequest(
        @NotNull @Nullable UUID fromProfileId,
        @NotNull @Nullable UUID toProfileId,
        @Size(max = 100) @Nullable String message,
        @Size(max = 20) @Nullable String stickerId,
        @Size(max = 20) @Nullable String emoji,
        @Nullable CheerKind kind,
        @Nullable UUID missionId,
        @Nullable UUID replyToCheerId) {
    /** stickerId 가 비었으면 emoji 를 스티커로 읽는다. */
    public @Nullable String effectiveStickerId() {
        if (!isNullOrBlank(stickerId)) return stickerId;
        return isNullOrBlank(emoji) ? null : emoji;
    }

    /** message/스티커 중 최소 하나. 검증용 getter 라 JSON 칸이 아니다(OpenAPI 에 contentPresent 로 싣지 않는다). */
    @JsonIgnore
    @AssertTrue(message = "message 나 stickerId 중 하나는 있어야 합니다")
    public boolean isContentPresent() {
        return !isNullOrBlank(message) || effectiveStickerId() != null;
    }

    private static boolean isNullOrBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
