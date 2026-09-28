package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LeagueRoundJpaRepository extends JpaRepository<LeagueRoundEntity, UUID> {
    List<LeagueRoundEntity> findByRoundMonthAndTierOrderByGroupNo(LocalDate roundMonth, String tier);

    List<LeagueRoundEntity> findByRoundMonthAndSettledAtIsNullOrderByTierAscGroupNoAsc(LocalDate roundMonth);

    /** 정산이 안 끝난 방만 끝난 것으로 적는다. 두 실행이 겹치면 늦은 쪽은 0 행을 받는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LeagueRoundEntity r set r.settledAt = :at where r.id = :id and r.settledAt is null")
    int markSettled(UUID id, Instant at);
}
