package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheerJpaRepository extends JpaRepository<CheerEntity, UUID> {
    long countByFromProfileIdAndToProfileIdAndCreatedAtAfter(UUID fromProfileId, UUID toProfileId, Instant after);

    long countByFamilyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID familyId, Instant from, Instant to);

    boolean existsByReplyToCheerId(UUID replyToCheerId);

    List<CheerEntity> findByToProfileIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            UUID toProfileId, Instant from, Instant to);
}
