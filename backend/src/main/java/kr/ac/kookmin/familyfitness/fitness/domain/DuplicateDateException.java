package kr.ac.kookmin.familyfitness.fitness.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 한 프로필의 같은 날짜 측정은 하나뿐이다. */
public class DuplicateDateException extends DomainException {
    public DuplicateDateException(LocalDate testedOn) {
        super("DUPLICATE_DATE", ErrorKind.CONFLICT, "같은 날짜의 측정이 이미 있습니다: " + testedOn);
    }
}
