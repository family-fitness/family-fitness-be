package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileJpaRepository extends JpaRepository<ProfileEntity, UUID> {
    List<ProfileEntity> findByFamilyIdOrderByCreatedAtAscIdAsc(UUID familyId);

    List<ProfileEntity> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    @Nullable
    ProfileEntity findByClaimCode(String claimCode);

    boolean existsByClaimCode(String claimCode);

    /**
     * 초대 코드 사용의 동시성 제어 — 조건부 UPDATE 한 문장. 영향 0행이면 다른 계정이 먼저 가져간 것.
     * 버전도 올린다. 그래야 이 계정 연결보다 먼저 가족을 읽어 둔 요청이 옛 행(user_id 없음)으로 덮지 못하고 409 로 끝난다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProfileEntity p set p.userId = :userId, p.claimCodeClaimedAt = :at, p.updatedAt = :at, "
            + "p.version = p.version + 1 where p.id = :id and p.userId is null")
    int attachUserIfUnclaimed(@Param("id") UUID id, @Param("userId") UUID userId, @Param("at") Instant at);
}
