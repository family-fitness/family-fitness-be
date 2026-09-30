package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 이미 승인·거절·실패로 끝난 실행을 다시 결정하려 했다. 승인 후면 `ALREADY_APPROVED`, 그 외는 `INVALID_STATE`. */
public class CoachRunAlreadyDecidedException extends DomainException {
    private final CoachRunStatus currentStatus;

    public CoachRunAlreadyDecidedException(CoachRunStatus currentStatus) {
        super(
                currentStatus == CoachRunStatus.APPROVED ? "ALREADY_APPROVED" : "INVALID_STATE",
                ErrorKind.CONFLICT,
                "이미 결정된 코치 실행입니다: " + currentStatus);
        this.currentStatus = currentStatus;
    }

    public CoachRunStatus getCurrentStatus() {
        return currentStatus;
    }
}
