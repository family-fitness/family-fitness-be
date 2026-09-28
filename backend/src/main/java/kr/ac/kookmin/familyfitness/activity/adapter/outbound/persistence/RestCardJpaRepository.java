package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RestCardJpaRepository extends JpaRepository<RestCardEntity, UUID> {
    List<RestCardEntity> findByFamilyIdAndRestMonthOrderByRestDate(UUID familyId, LocalDate restMonth);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RestCardEntity r where r.familyId = :familyId and r.restDate = :restDate")
    int deleteByFamilyAndDate(UUID familyId, LocalDate restDate);

    @Query("""
            select r.restDate from RestCardEntity r
            where r.familyId = :familyId and r.restDate between :from and :to
            order by r.restDate
            """)
    List<LocalDate> restDatesBetween(UUID familyId, LocalDate from, LocalDate to);
}
