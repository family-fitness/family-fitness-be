package kr.ac.kookmin.familyfitness.identity.api;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 이 계정은 이 프로필 이름으로 행동할 수 없다. 본인 프로필도, 대신할 수 있는 계정 없는 아이 프로필도 아니다. */
public class CannotActAsProfileException extends DomainException {
    public CannotActAsProfileException() {
        this("내 프로필이나 계정 없는 아이 프로필로만 할 수 있습니다");
    }

    public CannotActAsProfileException(String message) {
        super("FORBIDDEN", ErrorKind.FORBIDDEN, message);
    }
}
