package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.RefreshTokenRepository;
import kr.ac.kookmin.familyfitness.identity.domain.RefreshToken;
import org.jspecify.annotations.Nullable;

class InMemoryRefreshTokenRepository implements RefreshTokenRepository {
    final Map<UUID, RefreshToken> tokens = new LinkedHashMap<>();

    /** rotate 가 행을 보기 직전에 한 번 돈다. 같은 토큰으로 온 다른 요청이 먼저 회전을 끝낸 상황을 만든다. */
    @Nullable
    Runnable beforeRotate;

    @Override
    public void add(RefreshToken token) {
        if (tokens.containsKey(token.id())) throw new IllegalStateException("jti 중복: " + token.id());
        tokens.put(token.id(), token);
    }

    @Override
    public @Nullable RefreshToken findById(UUID id) {
        return tokens.get(id);
    }

    @Override
    public boolean rotate(UUID id, UUID replacedBy, Instant at) {
        Runnable hook = beforeRotate;
        if (hook != null) {
            beforeRotate = null;
            hook.run();
        }
        RefreshToken token = tokens.get(id);
        if (token == null || token.revoked()) return false;
        tokens.put(id, revoked(token, at, replacedBy));
        return true;
    }

    @Override
    public int revokeFamily(UUID familyId, Instant at) {
        List<RefreshToken> active = tokens.values().stream()
                .filter(it -> it.familyId().equals(familyId) && !it.revoked())
                .toList();
        active.forEach(it -> tokens.put(it.id(), revoked(it, at, null)));
        return active.size();
    }

    @Override
    public int deleteExpiredOrRevoked(Instant expiredBefore, Instant revokedBefore) {
        List<UUID> gone = tokens.values().stream()
                .filter(it -> it.expiresAt().isBefore(expiredBefore)
                        || (it.revokedAt() != null && it.revokedAt().isBefore(revokedBefore)))
                .map(RefreshToken::id)
                .toList();
        gone.forEach(tokens::remove);
        return gone.size();
    }

    private static RefreshToken revoked(RefreshToken token, Instant at, @Nullable UUID replacedBy) {
        return new RefreshToken(
                token.id(), token.userId(), token.familyId(), token.createdAt(), token.expiresAt(), at, replacedBy);
    }
}
