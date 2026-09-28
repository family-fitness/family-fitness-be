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

    /**
     * 정리. revoked_at 이 null 인 행은 {@code <} 비교가 참이 되지 않아 두 번째 조건에 걸리지 않는다.
     *
     * <p>expires_at · revoked_at 에 인덱스를 걸지 않아 표 전체를 한 번 읽는다(Seq Scan). 조건이 OR 라서 expires_at 한 칸
     * 인덱스나 (expires_at, revoked_at) 복합 인덱스로는 계획이 바뀌지 않고, 두 칸에 따로 걸어야 BitmapOr 가 된다. 그러면
     * refresh 요청마다 인덱스 쓰기가 둘 늘어난다. 이 표에는 최근 refresh-ttl 동안의 발급 기록만 남으므로 하루 한 번 전체를 읽는
     * 쪽을 골랐다. 정리에 걸린 시간은 {@code RefreshTokenSweeper} 로그에 남는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RefreshTokenEntity t
             where t.expiresAt < :expiredBefore
                or t.revokedAt < :revokedBefore
            """)
    int deleteExpiredOrRevoked(
            @Param("expiredBefore") Instant expiredBefore, @Param("revokedBefore") Instant revokedBefore);
}
