package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LeagueMemberJpaRepository extends JpaRepository<LeagueMemberEntity, LeagueMemberId> {
    Optional<LeagueMemberEntity> findByIdFamilyIdAndRoundMonth(UUID familyId, LocalDate roundMonth);

    Optional<LeagueMemberEntity> findFirstByIdFamilyIdAndRoundMonthLessThanOrderByRoundMonthDesc(
            UUID familyId, LocalDate roundMonth);

    List<LeagueMemberEntity> findByIdRoundIdOrderBySeatNo(UUID roundId);

    List<LeagueMemberEntity> findByRoundMonthOrderByJoinedAtAscIdFamilyIdAsc(LocalDate roundMonth);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update LeagueMemberEntity m
            set m.finalRate = :finalRate, m.finalRank = :finalRank, m.moved = :moved
            where m.id.roundId = :roundId and m.id.familyId = :familyId
            """)
    int saveResult(UUID roundId, UUID familyId, @Nullable Integer finalRate, @Nullable Integer finalRank, String moved);
}
