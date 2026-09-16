package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 승인되지 않은 실행의 제안을 미션으로 만들려 했다. 애플리케이션이 절대 만들면 안 되는 상태. */
public class CoachApprovalRequiredException extends DomainException {
    public CoachApprovalRequiredException() {
        this("승인 전에는 제안을 미션으로 만들 수 없습니다");
    }

    public CoachApprovalRequiredException(String message) {
        super("INVALID_STATE", ErrorKind.CONFLICT, message);
    }
}
