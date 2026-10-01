package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueErasureRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LeagueErasureAdapter implements LeagueErasureRepository {
    private final LeagueErasureJpaRepository rows;

    public LeagueErasureAdapter(LeagueErasureJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public void eraseFamily(UUID familyId) {
        rows.deleteMembers(familyId);
    }
}
