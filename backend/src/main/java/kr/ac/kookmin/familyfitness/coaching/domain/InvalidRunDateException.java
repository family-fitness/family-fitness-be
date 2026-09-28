package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 지난 날짜(오늘 KST 이전)는 편성하지 않는다. */
public class InvalidRunDateException extends DomainException {
    public InvalidRunDateException(LocalDate date, LocalDate today) {
        super("INVALID_DATE", ErrorKind.RULE_VIOLATION, "지난 날짜는 편성할 수 없습니다: date=" + date + ", today=" + today);
    }
}
