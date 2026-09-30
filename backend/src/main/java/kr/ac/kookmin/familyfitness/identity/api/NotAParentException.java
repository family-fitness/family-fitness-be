package kr.ac.kookmin.familyfitness.identity.api;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class NotAParentException extends DomainException {
    public NotAParentException() {
        this("보호자만 할 수 있습니다");
    }

    public NotAParentException(String message) {
        super("NOT_A_PARENT", ErrorKind.FORBIDDEN, message);
    }
}
