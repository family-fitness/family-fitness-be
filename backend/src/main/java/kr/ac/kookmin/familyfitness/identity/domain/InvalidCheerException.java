package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 응원 요청의 칸 조합이 kind 와 맞지 않는다(400). */
public class InvalidCheerException extends DomainException {
    public InvalidCheerException(String message) {
        super("BAD_REQUEST", ErrorKind.BAD_REQUEST, message);
    }
}
