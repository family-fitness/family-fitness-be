package kr.ac.kookmin.familyfitness.identity.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 응원에 붙인 missionId 가 이 가족의 미션이 아니다. 없는 미션과 다른 가족의 미션을 가르지 않는다(다른 가족 미션이 있는지 알리지 않게).
 * 코드는 coaching 과 FE 목이 쓰는 MISSION_NOT_FOUND 다.
 */
public class CheerMissionNotFoundException extends DomainException {
    public CheerMissionNotFoundException(UUID missionId) {
        super("MISSION_NOT_FOUND", ErrorKind.NOT_FOUND, "이 가족의 미션이 아닙니다: " + missionId);
    }
}
