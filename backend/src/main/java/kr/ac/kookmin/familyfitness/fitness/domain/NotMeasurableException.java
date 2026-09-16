package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 만 4세 미만은 규준이 없어 측정 대상이 아니다. */
public class NotMeasurableException extends DomainException {
    public NotMeasurableException() {
        this("만 4세 미만은 측정 대상이 아닙니다");
    }

    public NotMeasurableException(String message) {
        super("NOT_MEASURABLE", ErrorKind.RULE_VIOLATION, message);
    }
}
