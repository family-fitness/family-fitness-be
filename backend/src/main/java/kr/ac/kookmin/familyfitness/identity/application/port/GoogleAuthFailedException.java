package kr.ac.kookmin.familyfitness.identity.application.port;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;

public class GoogleAuthFailedException extends DomainException {
    public GoogleAuthFailedException() {
        this("구글 인증에 실패했습니다", null);
    }

    public GoogleAuthFailedException(String message) {
        this(message, null);
    }

    public GoogleAuthFailedException(String message, @Nullable Throwable cause) {
        super("GOOGLE_AUTH_FAILED", ErrorKind.UNAUTHORIZED, message, cause);
    }
}
