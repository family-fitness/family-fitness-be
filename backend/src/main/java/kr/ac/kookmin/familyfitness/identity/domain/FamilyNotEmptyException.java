package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 오너가 탈퇴하려는데 가족에 다른 프로필이 남아 있다. 구성원을 모두 내보낸 뒤에 탈퇴할 수 있다. */
public class FamilyNotEmptyException extends DomainException {
    public FamilyNotEmptyException() {
        super("FAMILY_NOT_EMPTY", ErrorKind.CONFLICT, "가족에 다른 구성원이 남아 있습니다. 구성원을 모두 내보낸 뒤에 탈퇴할 수 있습니다");
    }
}
