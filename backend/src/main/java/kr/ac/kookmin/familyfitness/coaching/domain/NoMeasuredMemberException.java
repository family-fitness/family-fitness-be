package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 편성 대상이 측정 대상(만 4세 이상)인데 측정 기록이 없다. 코드 이름은 FE 가 「첫 측정」 길을 붙인 그대로 둔다. */
public class NoMeasuredMemberException extends DomainException {
    public NoMeasuredMemberException(UUID profileId) {
        super("NO_MEASURED_MEMBER", ErrorKind.RULE_VIOLATION, "편성 대상의 측정 기록이 없습니다: profile=" + profileId);
    }
}
