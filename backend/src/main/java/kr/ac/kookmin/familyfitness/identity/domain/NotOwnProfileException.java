package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 본인 계정에 붙은 프로필만 다룰 수 있는 작업에 남의 프로필을 넣었다. */
public class NotOwnProfileException extends DomainException {
    public NotOwnProfileException() {
        this("본인 프로필만 다룰 수 있습니다");
    }

    public NotOwnProfileException(String message) {
        super("FORBIDDEN", ErrorKind.FORBIDDEN, message);
    }
}
