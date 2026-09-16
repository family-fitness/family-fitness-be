package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class InvalidMetricException extends DomainException {
    public InvalidMetricException(TargetMetric expected, TargetMetric actual) {
        super("INVALID_METRIC", ErrorKind.RULE_VIOLATION, expected + " 미션이 아닙니다 (실제: " + actual + ")");
    }
}
