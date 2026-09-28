package kr.ac.kookmin.familyfitness.identity.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 이 가족에 그 cheerId 의 응원이 없다. 다른 가족의 응원도 없는 것으로 본다. */
public class CheerNotFoundException extends DomainException {
    public CheerNotFoundException(UUID cheerId) {
        super("CHEER_NOT_FOUND", ErrorKind.NOT_FOUND, "응원이 없습니다: " + cheerId);
    }
}
