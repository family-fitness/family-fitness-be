package kr.ac.kookmin.familyfitness.identity.api;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class NotSameFamilyException extends DomainException {
    public NotSameFamilyException() {
        this("이 가족의 구성원이 아닙니다");
    }

    public NotSameFamilyException(String message) {
        super("NOT_SAME_FAMILY", ErrorKind.FORBIDDEN, message);
    }
}
