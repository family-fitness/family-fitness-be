package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachMessageCitationJpaRepository
        extends JpaRepository<CoachMessageCitationEntity, CoachMessageCitationId> {}
