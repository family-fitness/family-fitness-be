package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 미션에 그 번호의 칸이 없다. 칸 없는 미션은 1번만 있다(결정 35). FE 목과 같은 404 SESSION_NOT_FOUND. */
public class SessionNotFoundException extends DomainException {
    public SessionNotFoundException(UUID missionId, int position) {
        super("SESSION_NOT_FOUND", ErrorKind.NOT_FOUND, "그 칸이 없습니다: mission=" + missionId + " position=" + position);
    }
}
