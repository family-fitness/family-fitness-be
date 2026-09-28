package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import org.jspecify.annotations.Nullable;

/**
 * 가족 안의 응원 한 건. 별도 애그리게잇이며 보내는 쪽·받는 쪽은 프로필 ID 로만 가리킨다.
 * stickerId 는 FE 스티커 코드(예: star)이고, replyToCheerId 는 THANKS 가 답한 스티커의 cheerId 다.
 */
public record Cheer(
        UUID id,
        UUID familyId,
        UUID fromProfileId,
        UUID toProfileId,
        CheerKind kind,
        @Nullable String message,
        @Nullable String stickerId,
        @Nullable UUID missionId,
        @Nullable UUID replyToCheerId,
        Instant createdAt) {
    /** 같은 대상에게 분당 이 횟수를 넘기면 `TOO_MANY`. */
    public static final int MAX_PER_WINDOW = 5;

    public static final Duration WINDOW = Duration.ofMinutes(1);

    public Cheer {
        if (fromProfileId.equals(toProfileId)) throw new IllegalArgumentException("자기 자신에게는 보낼 수 없다");
        if (isNullOrBlank(message) && isNullOrBlank(stickerId)) {
            throw new IllegalArgumentException("메시지나 스티커 중 하나는 있어야 한다");
        }
        if (replyToCheerId != null && kind != CheerKind.THANKS) {
            throw new IllegalArgumentException("replyToCheerId 는 THANKS 에만 둔다");
        }
    }

    private static boolean isNullOrBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
