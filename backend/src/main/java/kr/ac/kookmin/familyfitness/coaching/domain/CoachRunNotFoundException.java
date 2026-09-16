package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class CoachRunNotFoundException extends DomainException {
    public CoachRunNotFoundException(UUID runId) {
        super("COACH_RUN_NOT_FOUND", ErrorKind.NOT_FOUND, "코치 실행이 없습니다: " + runId);
    }
}
