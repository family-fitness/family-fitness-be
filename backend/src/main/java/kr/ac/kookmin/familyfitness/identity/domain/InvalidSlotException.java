package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 운동할 수 있는 시간의 칸 값이 규칙({@link WeeklyAvailability})에 맞지 않는다. FE 목과 같은 400 INVALID_SLOT. */
public class InvalidSlotException extends DomainException {
    public InvalidSlotException() {
        this("요일, 시각, 시간 중 맞지 않는 값이 있습니다");
    }

    public InvalidSlotException(String message) {
        super("INVALID_SLOT", ErrorKind.BAD_REQUEST, message);
    }
}
