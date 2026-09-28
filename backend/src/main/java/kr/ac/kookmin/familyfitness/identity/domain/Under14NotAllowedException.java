package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 만 14세 미만은 보호자 자리에 서지 못한다 — 가족 만들기(owner) · PARENT 추가 · PARENT 생년월일 고치기 ·
 * 보호자 동의 기록. 만 14세 미만은 보호자 동의를 받아야 하는 쪽이다.
 */
public class Under14NotAllowedException extends DomainException {
    public Under14NotAllowedException() {
        this("만 14세 미만은 보호자가 될 수 없습니다");
    }

    public Under14NotAllowedException(String message) {
        super("UNDER_14_NOT_ALLOWED", ErrorKind.RULE_VIOLATION, message);
    }
}
