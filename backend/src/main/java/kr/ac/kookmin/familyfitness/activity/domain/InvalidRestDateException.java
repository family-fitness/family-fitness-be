package kr.ac.kookmin.familyfitness.activity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 쉬는 날 카드를 쓰거나 되돌릴 수 없는 날(지난 날 · 다른 달 · 날짜 형식이 틀림). */
public class InvalidRestDateException extends DomainException {
    public InvalidRestDateException(String message) {
        super("INVALID_DATE", ErrorKind.RULE_VIOLATION, message);
    }
}
