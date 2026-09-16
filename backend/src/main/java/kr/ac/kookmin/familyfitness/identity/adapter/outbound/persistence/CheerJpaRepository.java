package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheerJpaRepository extends JpaRepository<CheerEntity, UUID> {
    long countByFromProfileIdAndToProfileIdAndCreatedAtAfter(UUID fromProfileId, UUID toProfileId, Instant after);

    long countByFamilyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID familyId, Instant from, Instant to);
}
