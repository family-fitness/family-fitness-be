package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import java.util.Collection;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationErasureRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationErasureAdapter implements NotificationErasureRepository {
    private final NotificationErasureJpaRepository rows;

    public NotificationErasureAdapter(NotificationErasureJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public void eraseProfiles(Collection<UUID> profileIds) {
        if (!profileIds.isEmpty()) rows.deleteOfProfiles(profileIds);
    }

    @Override
    public void eraseCheers(Collection<UUID> cheerIds) {
        if (!cheerIds.isEmpty()) rows.deleteOfCheers(cheerIds);
    }

    @Override
    public void eraseMissions(Collection<UUID> missionIds) {
        if (!missionIds.isEmpty()) rows.deleteOfMissions(missionIds);
    }
}
