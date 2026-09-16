package kr.ac.kookmin.familyfitness.shared.web;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 실패를 `{"error": {"code", "message"}}` 로 바꾼다.
 * 도메인 예외의 {@link ErrorKind} → HTTP 상태 매핑은 여기 한 곳에만 있다.
 */
@RestControllerAdvice
public class ApiErrorHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException e) {
        if (e.getKind() == ErrorKind.UNAVAILABLE) log.warn("외부 의존성 장애: {} {}", e.getCode(), e.getMessage(), e);
        return ResponseEntity.status(statusOf(e.getKind()))
                .body(ApiError.of(e.getCode(), e.getMessage() != null ? e.getMessage() : e.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(it -> it.getField() + ": " + it.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = e.getBindingResult().getAllErrors().stream()
                    .map(it -> it.getDefaultMessage() != null ? it.getDefaultMessage() : "invalid")
                    .collect(Collectors.joining("; "));
        }
        return badRequest(detail);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return badRequest(e.getMessage() != null ? e.getMessage() : "요청 형식이 올바르지 않습니다");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", "존재하지 않는 경로입니다"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of(
                        "METHOD_NOT_ALLOWED", e.getMessage() != null ? e.getMessage() : "METHOD_NOT_ALLOWED"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> unauthenticated(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("UNAUTHORIZED", "인증이 필요합니다"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("FORBIDDEN", "권한이 없습니다"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }

    private ResponseEntity<ApiError> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("BAD_REQUEST", message));
    }

    public static HttpStatus statusOf(ErrorKind kind) {
        return switch (kind) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case GONE -> HttpStatus.GONE;
            case RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
            case TOO_MANY -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
