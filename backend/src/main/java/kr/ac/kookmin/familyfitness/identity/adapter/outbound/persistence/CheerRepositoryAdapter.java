package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import org.springframework.stereotype.Repository;

@Repository
public class CheerRepositoryAdapter implements CheerRepository {
    private final CheerJpaRepository jpa;
    private final EntityManager em;

    public CheerRepositoryAdapter(CheerJpaRepository jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    @Override
    public Cheer save(Cheer cheer) {
        em.persist(new CheerEntity(
                cheer.id(),
                cheer.familyId(),
                cheer.fromProfileId(),
                cheer.toProfileId(),
                cheer.missionId(),
                cheer.emoji(),
                cheer.message(),
                cheer.createdAt()));
        return cheer;
    }

    @Override
    public int countFromTo(UUID fromProfileId, UUID toProfileId, Instant after) {
        return (int) jpa.countByFromProfileIdAndToProfileIdAndCreatedAtAfter(fromProfileId, toProfileId, after);
    }

    @Override
    public int countInFamily(UUID familyId, Instant from, Instant to) {
        return (int) jpa.countByFamilyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(familyId, from, to);
    }
}
