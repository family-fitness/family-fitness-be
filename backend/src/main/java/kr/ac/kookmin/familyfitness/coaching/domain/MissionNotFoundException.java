package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class MissionNotFoundException extends DomainException {
    public MissionNotFoundException(UUID missionId) {
        super("MISSION_NOT_FOUND", ErrorKind.NOT_FOUND, "미션이 없습니다: " + missionId);
    }
}
