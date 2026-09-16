package kr.ac.kookmin.familyfitness.fitness.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 측정일은 미래일 수 없다. 계약의 공통 400 코드를 그대로 쓴다. */
public class FutureTestDateException extends DomainException {
    public FutureTestDateException(LocalDate testedOn) {
        super("BAD_REQUEST", ErrorKind.BAD_REQUEST, "측정일은 미래일 수 없습니다: " + testedOn);
    }
}
