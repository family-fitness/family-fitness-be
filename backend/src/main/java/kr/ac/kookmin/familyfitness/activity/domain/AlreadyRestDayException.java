package kr.ac.kookmin.familyfitness.activity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 그날은 이미 쉬는 날이다. */
public class AlreadyRestDayException extends DomainException {
    public AlreadyRestDayException() {
        super("ALREADY_REST_DAY", ErrorKind.CONFLICT, "이미 쉬는 날입니다");
    }
}
