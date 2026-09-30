package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 만 14세 미만 프로필은 보호자 동의(개인정보·건강정보 둘 다)가 있어야 저장·측정한다. */
public class GuardianConsentRequiredException extends DomainException {
    public GuardianConsentRequiredException() {
        this("보호자 동의가 필요합니다");
    }

    public GuardianConsentRequiredException(String message) {
        super("CONSENT_REQUIRED", ErrorKind.RULE_VIOLATION, message);
    }
}
