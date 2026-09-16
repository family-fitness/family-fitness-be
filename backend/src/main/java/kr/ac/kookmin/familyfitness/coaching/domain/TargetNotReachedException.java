package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class TargetNotReachedException extends DomainException {
    public TargetNotReachedException(double progress) {
        super("TARGET_NOT_REACHED", ErrorKind.RULE_VIOLATION, "아직 목표에 도달하지 않았습니다 (진행도 " + progress + ")");
    }
}
