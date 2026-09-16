package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class AlreadyMemberException extends DomainException {
    public AlreadyMemberException() {
        this("이미 이 가족의 구성원입니다");
    }

    public AlreadyMemberException(String message) {
        super("ALREADY_MEMBER", ErrorKind.CONFLICT, message);
    }
}
