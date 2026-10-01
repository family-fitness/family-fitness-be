package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.util.Collection;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.application.port.ProgressErasureRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ProgressErasureAdapter implements ProgressErasureRepository {
    private final ProgressErasureJpaRepository rows;

    public ProgressErasureAdapter(ProgressErasureJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public void eraseProfiles(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) return;
        rows.deleteXp(profileIds);
        rows.deleteAchievements(profileIds);
        rows.forgetSenders(profileIds);
    }

    @Override
    public void forgetMissions(Collection<UUID> missionIds) {
        if (!missionIds.isEmpty()) rows.forgetMissions(missionIds);
    }
}
