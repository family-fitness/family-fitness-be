package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class CoachRunInProgressException extends DomainException {
    public CoachRunInProgressException(UUID familyId) {
        super("RUN_IN_PROGRESS", ErrorKind.CONFLICT, "실행 중인 코치 실행이 있습니다: family=" + familyId);
    }
}
