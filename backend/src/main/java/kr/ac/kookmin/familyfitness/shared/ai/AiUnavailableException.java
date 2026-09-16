package kr.ac.kookmin.familyfitness.shared.ai;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;

/** LLM·인덱스 일시 장애, 타임아웃, 연결 실패 → 503. */
public class AiUnavailableException extends DomainException {
    public AiUnavailableException(String message) {
        this(message, null);
    }

    public AiUnavailableException(String message, @Nullable Throwable cause) {
        super("TEMPORARILY_UNAVAILABLE", ErrorKind.UNAVAILABLE, message, cause);
    }
}
