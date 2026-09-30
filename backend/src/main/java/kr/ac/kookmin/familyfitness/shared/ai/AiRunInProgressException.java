package kr.ac.kookmin.familyfitness.shared.ai;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class AiRunInProgressException extends DomainException {
    public AiRunInProgressException(String message) {
        super("RUN_IN_PROGRESS", ErrorKind.CONFLICT, message);
    }
}
