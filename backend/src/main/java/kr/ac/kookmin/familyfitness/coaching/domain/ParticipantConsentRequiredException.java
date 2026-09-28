package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 보호자 동의가 없거나 거둔 프로필은 편성 · 미션 · 활동 기록에 넣지 않는다. */
public class ParticipantConsentRequiredException extends DomainException {
    public ParticipantConsentRequiredException() {
        super("CONSENT_REQUIRED", ErrorKind.RULE_VIOLATION, "보호자 동의가 필요한 참여자가 있습니다");
    }
}
