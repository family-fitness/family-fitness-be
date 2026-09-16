package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 항목 0개면 저장하지 않는다. */
public class NoItemsException extends DomainException {
    public NoItemsException() {
        super("NO_ITEMS", ErrorKind.BAD_REQUEST, "측정 항목이 하나 이상 있어야 합니다");
    }
}
