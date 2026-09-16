package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 카탈로그에 없는 코드(신체조성 003·004·018·042 포함). */
public class UnknownItemException extends DomainException {
    public UnknownItemException(String itemCode) {
        super("UNKNOWN_ITEM", ErrorKind.BAD_REQUEST, "알 수 없는 측정 항목입니다: " + itemCode);
    }
}
