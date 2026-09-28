package kr.ac.kookmin.familyfitness.league.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 아직 오지 않은 달의 리그를 물었다. 코드는 FE 가 아는 INVALID_DATE 를 쓴다(쉬는 날 카드와 같다). */
public class InvalidLeagueMonthException extends DomainException {
    public InvalidLeagueMonthException(String message) {
        super("INVALID_DATE", ErrorKind.RULE_VIOLATION, message);
    }
}
