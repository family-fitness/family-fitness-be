package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 이 가족의 구성원이 아닌 계정이 가족 자원을 건드렸다. */
public class FamilyAccessDeniedException extends DomainException {
    public FamilyAccessDeniedException() {
        this("이 가족의 구성원이 아닙니다");
    }

    public FamilyAccessDeniedException(String message) {
        super("NOT_SAME_FAMILY", ErrorKind.FORBIDDEN, message);
    }
}
