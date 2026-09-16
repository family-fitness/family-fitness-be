package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionJpaRepository extends JpaRepository<MissionEntity, UUID> {
    List<MissionEntity> findByFamilyId(UUID familyId);

    List<MissionEntity> findByFamilyIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
            UUID familyId, LocalDate to, LocalDate from);

    long countByCoachRunId(UUID coachRunId);
}
