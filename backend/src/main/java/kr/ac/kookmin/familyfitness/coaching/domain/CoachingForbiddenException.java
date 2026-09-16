package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class CoachingForbiddenException extends DomainException {
    public CoachingForbiddenException(String message) {
        super("FORBIDDEN", ErrorKind.FORBIDDEN, message);
    }
}
