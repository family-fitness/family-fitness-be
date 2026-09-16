package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionJpaRepository extends JpaRepository<PredictionEntity, UUID> {}
