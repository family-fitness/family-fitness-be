package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyInviteJpaRepository extends JpaRepository<FamilyInviteEntity, String> {
    List<FamilyInviteEntity> findByFamilyIdAndClaimedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescCodeAsc(
            UUID familyId, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FamilyInviteEntity i where i.familyId = :familyId and i.code = :code and i.claimedAt is null")
    int deleteUnclaimed(@Param("familyId") UUID familyId, @Param("code") String code);
}
