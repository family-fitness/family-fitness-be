package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 영상 재생 시간이 칸 시간의 절반도 안 된다(결정 3-1, FE 목 422 TOO_SHORT). */
public class SessionTooShortException extends DomainException {
    public SessionTooShortException(long activeSeconds, int plannedSeconds) {
        super(
                "TOO_SHORT",
                ErrorKind.RULE_VIOLATION,
                "잡힌 시간의 절반도 하지 않았습니다: " + activeSeconds + "초 / " + plannedSeconds + "초");
    }
}
