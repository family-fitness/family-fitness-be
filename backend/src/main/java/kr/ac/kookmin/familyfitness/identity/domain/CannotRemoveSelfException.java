package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 오너가 자기 프로필을 내보내려 했다. 자기는 내보내지 않고 탈퇴(DELETE /me)로 나간다. */
public class CannotRemoveSelfException extends DomainException {
    public CannotRemoveSelfException() {
        super("CANNOT_REMOVE_SELF", ErrorKind.CONFLICT, "자기 프로필은 내보낼 수 없습니다. 탈퇴로 나가 주세요");
    }
}
