package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 누군가 칸을 끝냈거나 참여자가 완료된 미션은 지우지 않는다(결정 40). FE 목에 코드가 없어 새로 둔다. */
public class MissionAlreadyStartedException extends DomainException {
    public MissionAlreadyStartedException(UUID missionId) {
        super("MISSION_ALREADY_STARTED", ErrorKind.CONFLICT, "이미 누군가 한 운동이라 지울 수 없습니다: " + missionId);
    }
}
