package kr.ac.kookmin.familyfitness.shared.domain;

import org.jspecify.annotations.Nullable;

/**
 * 모든 모듈의 업무 예외가 상속하는 기반 예외.
 * {@code code} 는 API 응답 `error.code` 로 그대로 나간다. {@code message} 는 개발자용이며 화면에 노출하지 않는다.
 */
public class DomainException extends RuntimeException {
    private final String code;
    private final ErrorKind kind;

    public DomainException(String code, ErrorKind kind, String message) {
        this(code, kind, message, null);
    }

    public DomainException(String code, ErrorKind kind, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
        this.kind = kind;
    }

    public String getCode() {
        return code;
    }

    public ErrorKind getKind() {
        return kind;
    }
}
