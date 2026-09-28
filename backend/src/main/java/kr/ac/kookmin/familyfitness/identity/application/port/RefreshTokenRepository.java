package kr.ac.kookmin.familyfitness.identity.application.port;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.RefreshToken;
import org.jspecify.annotations.Nullable;

/** 리프레시 토큰 발급 기록(refresh_tokens). */
public interface RefreshTokenRepository {
    /** 새로 발급한 토큰을 기록한다. */
    void add(RefreshToken token);

    @Nullable
    RefreshToken findById(UUID id);

    /**
     * 아직 폐기되지 않은 토큰만 폐기하고 다음 토큰의 jti 를 남긴다. 이미 폐기돼 있으면 아무것도 바꾸지 않고 false 다.
     * 같은 토큰으로 두 요청이 동시에 와도 한쪽만 true 를 받는다(조건부 UPDATE 한 문장).
     */
    boolean rotate(UUID id, UUID replacedBy, Instant at);

    /** 한 묶음(familyId)에서 아직 살아 있는 토큰을 모두 폐기하고, 바꾼 행 수를 돌려준다. */
    int revokeFamily(UUID familyId, Instant at);

    /**
     * expires_at 이 {@code expiredBefore} 보다 이르거나 revoked_at 이 {@code revokedBefore} 보다 이른 행을 지우고,
     * 지운 행 수를 돌려준다. 폐기되지 않은 행(revoked_at null)은 두 번째 조건에 걸리지 않는다.
     */
    int deleteExpiredOrRevoked(Instant expiredBefore, Instant revokedBefore);
}
