package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 가족 안의 응원 한 건. 별도 애그리게잇이며 보내는 쪽·받는 쪽은 프로필 ID 로만 가리킨다. */
public record Cheer(
        UUID id,
        UUID familyId,
        UUID fromProfileId,
        UUID toProfileId,
        @Nullable String message,
        @Nullable String emoji,
        @Nullable UUID missionId,
        Instant createdAt) {
    /** 같은 대상에게 분당 이 횟수를 넘기면 `TOO_MANY`. */
    public static final int MAX_PER_WINDOW = 5;

    public static final Duration WINDOW = Duration.ofMinutes(1);

    public Cheer {
        if (fromProfileId.equals(toProfileId)) throw new IllegalArgumentException("자기 자신에게는 보낼 수 없다");
        if (isNullOrBlank(message) && isNullOrBlank(emoji)) {
            throw new IllegalArgumentException("메시지나 이모지 중 하나는 있어야 한다");
        }
    }

    private static boolean isNullOrBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
