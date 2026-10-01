package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 가족을 만든 보호자(오너)만 할 수 있는 일을 다른 구성원이 했다. 지금은 구성원 내보내기다. */
public class NotFamilyOwnerException extends DomainException {
    public NotFamilyOwnerException() {
        super("NOT_FAMILY_OWNER", ErrorKind.FORBIDDEN, "가족을 만든 보호자만 할 수 있습니다");
    }
}
