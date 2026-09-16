package kr.ac.kookmin.familyfitness.shared.ai;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class AiRunNotFoundException extends DomainException {
    public AiRunNotFoundException(String runId) {
        super("RUN_NOT_FOUND", ErrorKind.NOT_FOUND, "AI run 이 없습니다: " + runId);
    }
}
