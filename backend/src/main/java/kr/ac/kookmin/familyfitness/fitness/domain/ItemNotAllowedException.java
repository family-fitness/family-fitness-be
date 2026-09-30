package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 005·006(혈압)은 입력으로 받지 않는다. */
public class ItemNotAllowedException extends DomainException {
    public ItemNotAllowedException(String itemCode) {
        super("ITEM_NOT_ALLOWED", ErrorKind.BAD_REQUEST, "입력할 수 없는 항목입니다: " + itemCode);
    }
}
