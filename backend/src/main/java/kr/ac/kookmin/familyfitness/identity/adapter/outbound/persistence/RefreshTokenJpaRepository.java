package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    /**
     * 회전. 폐기되지 않은 행만 바꾼다 — 같은 토큰으로 온 두 번째 요청은 0행을 받는다. 동시에 온 두 요청이면 늦은 쪽이 행 잠금을
     * 기다렸다가 WHERE 를 다시 평가해서 0행이 된다(READ COMMITTED 일 때. AuthService 설명 참고).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :at, t.replacedBy = :replacedBy
             where t.jti = :jti and t.revokedAt is null
            """)
    int rotateIfActive(@Param("jti") UUID jti, @Param("replacedBy") UUID replacedBy, @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :at
             where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeActiveOfFamily(@Param("familyId") UUID familyId, @Param("at") Instant at);
}
