package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityErasureRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ActivityErasureAdapter implements ActivityErasureRepository {
    private final ActivityErasureJpaRepository rows;

    public ActivityErasureAdapter(ActivityErasureJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public void eraseProfile(UUID profileId, UUID heirProfileId) {
        rows.deleteDaily(List.of(profileId));
        rows.handOverRestCards(profileId, heirProfileId);
    }

    @Override
    public void eraseRecords(UUID profileId) {
        rows.deleteDaily(List.of(profileId));
    }

    @Override
    public void eraseFamily(UUID familyId, Collection<UUID> profileIds) {
        if (!profileIds.isEmpty()) rows.deleteDaily(profileIds);
        rows.deleteRestCards(familyId);
    }
}
