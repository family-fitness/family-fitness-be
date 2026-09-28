package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 없는 초대코드를 짧은 시간에 너무 많이 넣었다. 코드를 차례로 넣어 맞히는 시도를 막는다. */
public class TooManyClaimAttemptsException extends DomainException {
    public TooManyClaimAttemptsException() {
        super("TOO_MANY", ErrorKind.TOO_MANY, "초대코드를 너무 많이 틀렸습니다. 잠시 뒤에 다시 넣어 주세요");
    }
}
