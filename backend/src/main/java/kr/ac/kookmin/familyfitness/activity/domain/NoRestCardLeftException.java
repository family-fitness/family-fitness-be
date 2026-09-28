package kr.ac.kookmin.familyfitness.activity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 그달 쉬는 날 카드를 다 썼다. */
public class NoRestCardLeftException extends DomainException {
    public NoRestCardLeftException() {
        super("NO_REST_CARD_LEFT", ErrorKind.CONFLICT, "이번 달 카드를 다 썼습니다");
    }
}
