package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 보호자 동의는 아이(CHILD) 프로필에만 있다. 보호자(PARENT) 프로필의 동의는 다른 보호자가 주거나 거두지 못한다 —
 * 거두면 그 보호자의 칸 끝 · 측정 · 미션 참여가 막히는데 본인은 되돌릴 수 없었다(QA KP-01).
 */
public class ConsentNotApplicableException extends DomainException {
    public ConsentNotApplicableException() {
        super("CONSENT_NOT_APPLICABLE", ErrorKind.RULE_VIOLATION, "보호자 동의는 아이 프로필에만 있습니다");
    }
}
