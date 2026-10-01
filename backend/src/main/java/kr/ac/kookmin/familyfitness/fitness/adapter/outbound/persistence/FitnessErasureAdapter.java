package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.util.Collection;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessErasureRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class FitnessErasureAdapter implements FitnessErasureRepository {
    private final FitnessErasureJpaRepository rows;

    public FitnessErasureAdapter(FitnessErasureJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public void eraseProfiles(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) return;
        rows.deleteItems(profileIds);
        rows.deleteTests(profileIds);
    }
}
