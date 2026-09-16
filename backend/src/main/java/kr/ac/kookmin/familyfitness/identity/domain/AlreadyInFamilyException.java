package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class AlreadyInFamilyException extends DomainException {
    public AlreadyInFamilyException() {
        this("이 계정에 이미 프로필이 붙어 있습니다");
    }

    public AlreadyInFamilyException(String message) {
        super("ALREADY_IN_FAMILY", ErrorKind.CONFLICT, message);
    }
}
