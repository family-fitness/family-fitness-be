package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class SelfCheerException extends DomainException {
    public SelfCheerException() {
        this("자기 자신에게는 응원을 보낼 수 없습니다");
    }

    public SelfCheerException(String message) {
        super("SELF_CHEER", ErrorKind.RULE_VIOLATION, message);
    }
}
