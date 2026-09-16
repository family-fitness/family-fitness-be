package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class ClaimCodeExpiredException extends DomainException {
    public ClaimCodeExpiredException() {
        this("만료된 초대 코드입니다");
    }

    public ClaimCodeExpiredException(String message) {
        super("CODE_EXPIRED", ErrorKind.GONE, message);
    }
}
