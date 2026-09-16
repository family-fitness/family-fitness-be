package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyJpaRepository extends JpaRepository<FamilyEntity, UUID> {}
