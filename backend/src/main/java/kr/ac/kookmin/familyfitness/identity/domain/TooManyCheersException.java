package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class TooManyCheersException extends DomainException {
    public TooManyCheersException() {
        this("같은 대상에게 보낼 수 있는 응원 횟수를 넘었습니다");
    }

    public TooManyCheersException(String message) {
        super("TOO_MANY", ErrorKind.TOO_MANY, message);
    }
}
