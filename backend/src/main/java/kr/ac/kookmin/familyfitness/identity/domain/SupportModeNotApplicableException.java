package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 참여 수준은 PARENT 프로필에만 있는 개념이다. */
public class SupportModeNotApplicableException extends DomainException {
    public SupportModeNotApplicableException() {
        this("아이 프로필에는 참여 수준이 없습니다");
    }

    public SupportModeNotApplicableException(String message) {
        super("NOT_APPLICABLE", ErrorKind.RULE_VIOLATION, message);
    }
}
