package kr.ac.kookmin.familyfitness.activity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 되돌리려는 날이 쉬는 날이 아니다. */
public class NotRestDayException extends DomainException {
    public NotRestDayException() {
        super("NOT_REST_DAY", ErrorKind.NOT_FOUND, "쉬는 날이 아닙니다");
    }
}
