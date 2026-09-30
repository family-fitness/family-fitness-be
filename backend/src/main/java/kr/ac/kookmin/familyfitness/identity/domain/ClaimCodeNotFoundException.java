package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class ClaimCodeNotFoundException extends DomainException {
    public ClaimCodeNotFoundException() {
        this("초대 코드를 찾을 수 없습니다");
    }

    public ClaimCodeNotFoundException(String message) {
        super("CODE_NOT_FOUND", ErrorKind.NOT_FOUND, message);
    }
}
