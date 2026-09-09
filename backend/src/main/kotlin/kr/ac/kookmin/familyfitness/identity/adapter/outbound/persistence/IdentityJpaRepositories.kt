package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface UserJpaRepository : JpaRepository<UserEntity, UUID> {
    fun findByProviderAndProviderUserId(
        provider: String,
        providerUserId: String,
    ): UserEntity?
}

interface FamilyJpaRepository : JpaRepository<FamilyEntity, UUID>

interface ProfileJpaRepository : JpaRepository<ProfileEntity, UUID> {
    fun findByFamilyIdOrderByCreatedAtAscIdAsc(familyId: UUID): List<ProfileEntity>

    fun findByUserIdOrderByCreatedAtAscIdAsc(userId: UUID): List<ProfileEntity>

    fun findByClaimCode(claimCode: String): ProfileEntity?

    fun existsByClaimCode(claimCode: String): Boolean

    /** 초대 코드 사용의 동시성 제어 — 조건부 UPDATE 한 문장. 영향 0행이면 다른 계정이 먼저 가져간 것. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update ProfileEntity p set p.userId = :userId, p.claimCodeClaimedAt = :at, p.updatedAt = :at " +
            "where p.id = :id and p.userId is null",
    )
    fun attachUserIfUnclaimed(
        @Param("id") id: UUID,
        @Param("userId") userId: UUID,
        @Param("at") at: Instant,
    ): Int
}

interface CheerJpaRepository : JpaRepository<CheerEntity, UUID> {
    fun countByFromProfileIdAndToProfileIdAndCreatedAtAfter(
        fromProfileId: UUID,
        toProfileId: UUID,
        after: Instant,
    ): Long

    fun countByFamilyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        familyId: UUID,
        from: Instant,
        to: Instant,
    ): Long
}
