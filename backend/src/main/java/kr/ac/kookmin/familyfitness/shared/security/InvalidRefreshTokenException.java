package kr.ac.kookmin.familyfitness.shared.security;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;

public class InvalidRefreshTokenException extends DomainException {
    public InvalidRefreshTokenException() {
        this("리프레시 토큰이 유효하지 않습니다", null);
    }

    public InvalidRefreshTokenException(String message) {
        this(message, null);
    }

    public InvalidRefreshTokenException(@Nullable Throwable cause) {
        this("리프레시 토큰이 유효하지 않습니다", cause);
    }

    public InvalidRefreshTokenException(String message, @Nullable Throwable cause) {
        super("INVALID_REFRESH_TOKEN", ErrorKind.UNAUTHORIZED, message, cause);
    }
}
