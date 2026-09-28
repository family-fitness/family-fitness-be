package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 보호자 동의는 다른 구성원에게 하는 것이다. 자기 프로필의 동의를 스스로 주거나 거두지 못한다. */
public class SelfConsentException extends DomainException {
    public SelfConsentException() {
        this("자기 프로필의 보호자 동의는 바꿀 수 없습니다");
    }

    public SelfConsentException(String message) {
        super("SELF_CONSENT", ErrorKind.FORBIDDEN, message);
    }
}
