package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class NoMeasuredMemberException extends DomainException {
    public NoMeasuredMemberException(UUID familyId) {
        super("NO_MEASURED_MEMBER", ErrorKind.RULE_VIOLATION, "측정 기록이 있는 구성원이 없습니다: family=" + familyId);
    }
}
