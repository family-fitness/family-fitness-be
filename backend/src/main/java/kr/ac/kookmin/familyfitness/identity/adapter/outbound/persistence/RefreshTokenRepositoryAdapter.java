package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.RefreshTokenRepository;
import kr.ac.kookmin.familyfitness.identity.domain.RefreshToken;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {
    private final RefreshTokenJpaRepository jpa;
    private final EntityManager em;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    @Override
    public void add(RefreshToken token) {
        // jti 를 앱이 정하므로 save(merge) 대신 persist 로 넣는다 — merge 는 없는 행을 찾는 SELECT 를 한 번 더 한다.
        em.persist(new RefreshTokenEntity(
                token.id(),
                token.userId(),
                token.familyId(),
                token.expiresAt(),
                token.revokedAt(),
                token.replacedBy(),
                token.createdAt()));
    }

    @Override
    public @Nullable RefreshToken findById(UUID id) {
        return jpa.findById(id).map(RefreshTokenRepositoryAdapter::toDomain).orElse(null);
    }

    @Override
    public boolean rotate(UUID id, UUID replacedBy, Instant at) {
        return jpa.rotateIfActive(id, replacedBy, at) == 1;
    }

    @Override
    public int revokeFamily(UUID familyId, Instant at) {
        return jpa.revokeActiveOfFamily(familyId, at);
    }

    private static RefreshToken toDomain(RefreshTokenEntity entity) {
        return new RefreshToken(
                entity.getJti(),
                entity.getUserId(),
                entity.getFamilyId(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getReplacedBy());
    }
}
