package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 같은 항목이 한 요청에 두 번 들어왔다. ▲ 확정 필요 — 계약에 없는 경우라 공통 400 코드로 낸다. */
public class DuplicateItemException extends DomainException {
    public DuplicateItemException(String itemCode) {
        super("BAD_REQUEST", ErrorKind.BAD_REQUEST, "같은 항목이 두 번 들어왔습니다: " + itemCode);
    }
}
