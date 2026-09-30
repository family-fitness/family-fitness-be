package kr.ac.kookmin.familyfitness.fitness.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class FitnessTestNotFoundException extends DomainException {
    public FitnessTestNotFoundException(UUID fitnessTestId) {
        super("FITNESS_TEST_NOT_FOUND", ErrorKind.NOT_FOUND, "측정 기록이 없습니다: " + fitnessTestId);
    }
}
