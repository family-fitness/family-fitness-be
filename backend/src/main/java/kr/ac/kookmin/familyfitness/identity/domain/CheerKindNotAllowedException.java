package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 이 kind 는 이 방향(보내는 쪽 역할 → 받는 쪽 역할)으로 보낼 수 없다. */
public class CheerKindNotAllowedException extends DomainException {
    public CheerKindNotAllowedException(String message) {
        super("CHEER_KIND_NOT_ALLOWED", ErrorKind.RULE_VIOLATION, message);
    }
}
