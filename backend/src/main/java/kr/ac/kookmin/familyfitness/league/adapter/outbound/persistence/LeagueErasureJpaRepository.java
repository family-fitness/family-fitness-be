package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 가족을 지울 때의 삭제 쿼리. */
public interface LeagueErasureJpaRepository extends Repository<LeagueMemberEntity, LeagueMemberId> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from league_members where family_id = :familyId", nativeQuery = true)
    int deleteMembers(UUID familyId);
}
