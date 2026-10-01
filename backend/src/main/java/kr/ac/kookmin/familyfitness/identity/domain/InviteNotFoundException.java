package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 취소할 가족 초대가 그 가족에 없다. 없는 코드, 이미 쓴 코드, 다른 가족의 코드가 모두 여기다. */
public class InviteNotFoundException extends DomainException {
    public InviteNotFoundException() {
        super("INVITE_NOT_FOUND", ErrorKind.NOT_FOUND, "취소할 초대가 없습니다");
    }
}
