package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class NotFamilyMemberException extends DomainException {
    public NotFamilyMemberException() {
        this("같은 가족의 프로필이 아닙니다");
    }

    public NotFamilyMemberException(String message) {
        super("NOT_FAMILY_MEMBER", ErrorKind.RULE_VIOLATION, message);
    }
}
