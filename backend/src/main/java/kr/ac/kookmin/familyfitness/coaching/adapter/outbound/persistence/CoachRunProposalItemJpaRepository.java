package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachRunProposalItemJpaRepository extends JpaRepository<CoachRunProposalItemEntity, ProposalItemId> {
    List<CoachRunProposalItemEntity> findByIdCoachRunIdOrderByIdPosition(UUID coachRunId);

    void deleteByIdCoachRunId(UUID coachRunId);
}
