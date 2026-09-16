package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class AlreadyRunThisWeekException extends DomainException {
    public AlreadyRunThisWeekException(LocalDate weekStart) {
        super("ALREADY_RUN_THIS_WEEK", ErrorKind.CONFLICT, "이번 주(" + weekStart + ") 편성이 이미 있습니다");
    }
}
