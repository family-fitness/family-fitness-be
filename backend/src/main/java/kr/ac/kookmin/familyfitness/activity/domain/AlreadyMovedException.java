package kr.ac.kookmin.familyfitness.activity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 그날 이미 운동한 아이가 있어 쉬는 날로 둘 수 없다. */
public class AlreadyMovedException extends DomainException {
    public AlreadyMovedException() {
        super("ALREADY_MOVED", ErrorKind.RULE_VIOLATION, "이미 운동한 날입니다");
    }
}
