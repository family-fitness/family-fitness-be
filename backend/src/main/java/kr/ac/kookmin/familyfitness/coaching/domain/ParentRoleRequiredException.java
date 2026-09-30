package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class ParentRoleRequiredException extends DomainException {
    public ParentRoleRequiredException() {
        this("보호자만 승인·거절할 수 있습니다");
    }

    public ParentRoleRequiredException(String message) {
        super("NOT_A_PARENT", ErrorKind.FORBIDDEN, message);
    }
}
