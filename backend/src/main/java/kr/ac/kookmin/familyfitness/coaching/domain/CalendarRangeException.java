package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 캘린더 기간이 거꾸로이거나 너무 길거나, 날짜로 셈할 수 있는 범위 밖이다 — 400 BAD_REQUEST(FE 목도 400 BAD_REQUEST 다,
 * fe:src/mocks/history.ts:241). 한 번에 받는 날 수 상한은 FE 요청서 3장 · ASKS 0-2 의 「최대 42일」(양끝 포함)이다.
 */
public class CalendarRangeException extends DomainException {
    public CalendarRangeException(LocalDate from, LocalDate to, int maxDays) {
        super(
                "BAD_REQUEST",
                ErrorKind.BAD_REQUEST,
                "기간은 from ≤ to 이고 양끝을 넣어 " + maxDays + "일 이하여야 합니다: " + from + " ~ " + to);
    }

    public CalendarRangeException(LocalDate from, LocalDate to, LocalDate earliest, LocalDate latest) {
        super(
                "BAD_REQUEST",
                ErrorKind.BAD_REQUEST,
                "날짜는 " + earliest + " ~ " + latest + " 안이어야 합니다: " + from + " ~ " + to);
    }
}
