package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 예측하려면 측정 기록이 하나는 있어야 한다. */
public class NoFitnessTestException extends DomainException {
    public NoFitnessTestException() {
        super("NO_FITNESS_TEST", ErrorKind.RULE_VIOLATION, "측정 기록이 없습니다");
    }
}
