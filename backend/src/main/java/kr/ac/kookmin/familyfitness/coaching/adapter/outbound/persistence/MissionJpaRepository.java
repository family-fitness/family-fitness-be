package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MissionJpaRepository extends JpaRepository<MissionEntity, UUID> {
    List<MissionEntity> findByFamilyId(UUID familyId);

    List<MissionEntity> findByFamilyIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
            UUID familyId, LocalDate to, LocalDate from);

    /** 이 날이 기간 안에 드는 미션이 있는 가족. */
    @Query("select distinct m.familyId from MissionEntity m where m.startsOn <= :day and m.endsOn >= :day")
    List<UUID> findFamilyIdsOn(LocalDate day);

    long countByCoachRunId(UUID coachRunId);

    /**
     * 미션 행을 SELECT … FOR UPDATE 로 잠근다. JPA 잠금({@code @Lock(PESSIMISTIC_WRITE)})을 쓰지 않고 SQL 을 직접 쓰는 까닭:
     * Hibernate 7 은 PostgreSQL 에서 PESSIMISTIC_WRITE 를 {@code FOR NO KEY UPDATE} 로 낸다. 칸 끝 행을 넣을 때 외래 키 검사가
     * 미션 행에 거는 {@code FOR KEY SHARE} 는 그 잠금과 부딪치지 않아, 지우기와 칸 끝이 서로를 기다리지 않는다.
     * {@code FOR UPDATE} 는 {@code FOR KEY SHARE} 와 부딪친다. PostgreSQL · H2 둘 다 이 문법을 받는다.
     */
    @Query(value = "select m.* from missions m where m.id = :id for update", nativeQuery = true)
    Optional<MissionEntity> findForUpdate(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MissionEntity m where m.id = :id")
    int deleteOne(UUID id);
}
