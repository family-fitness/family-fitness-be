package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.identity.api.MissionLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** identity 가 응원의 missionId 를 검사할 때 부른다({@link MissionLookup} 구현). */
@Service
@Transactional(readOnly = true)
public class MissionLookupService implements MissionLookup {
    private final MissionRepository missions;

    public MissionLookupService(MissionRepository missions) {
        this.missions = missions;
    }

    @Override
    public boolean isFamilyMission(UUID familyId, UUID missionId) {
        Mission mission = missions.findById(missionId);
        return mission != null && mission.getFamilyId().equals(familyId);
    }
}
