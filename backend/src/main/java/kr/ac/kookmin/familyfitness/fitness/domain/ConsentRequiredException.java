package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 만 14세 미만은 보호자 동의가 살아 있어야 저장한다. */
public class ConsentRequiredException extends DomainException {
    public ConsentRequiredException() {
        this("보호자 동의가 필요합니다");
    }

    public ConsentRequiredException(String message) {
        super("CONSENT_REQUIRED", ErrorKind.RULE_VIOLATION, message);
    }
}
