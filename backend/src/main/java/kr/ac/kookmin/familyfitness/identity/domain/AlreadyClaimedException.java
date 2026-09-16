package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class AlreadyClaimedException extends DomainException {
    public AlreadyClaimedException() {
        this("이미 계정이 붙은 프로필입니다");
    }

    public AlreadyClaimedException(String message) {
        super("ALREADY_CLAIMED", ErrorKind.CONFLICT, message);
    }
}
