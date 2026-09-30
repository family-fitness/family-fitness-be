package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class NotFamilyMemberException extends DomainException {
    public NotFamilyMemberException(UUID profileId) {
        super("NOT_FAMILY_MEMBER", ErrorKind.RULE_VIOLATION, "이 가족의 구성원이 아닙니다: profile=" + profileId);
    }
}
