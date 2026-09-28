package kr.ac.kookmin.familyfitness.shared.web;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.sql.SQLException;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;

/**
 * 모든 실패를 `{"error": {"code", "message"}}` 로 바꾼다.
 * 도메인 예외의 {@link ErrorKind} → HTTP 상태 매핑과, 표준 HTTP 상태 → `error.code` 표({@link #codeOf})는 여기 한 곳에만 있다.
 * 응답 Content-Type 은 JSON 으로 고정한다 — Accept 가 JSON 을 받지 않아도(406) 봉투가 나가게.
 */
@RestControllerAdvice
public class ApiErrorHandler {
    static final String SERVER_ERROR_MESSAGE = "서버 오류가 발생했습니다";
    private static final String BAD_REQUEST_MESSAGE = "요청 형식이 올바르지 않습니다";
    private static final String UNREADABLE_BODY_MESSAGE = "요청 본문을 읽을 수 없습니다";
    /** SQLSTATE 23505 unique_violation. PostgreSQL · H2 모두 이 값을 쓴다. */
    private static final String UNIQUE_VIOLATION = "23505";

    private final Logger log = LoggerFactory.getLogger(getClass());

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException e) {
        if (e.getKind() == ErrorKind.UNAVAILABLE) log.warn("외부 의존성 장애: {} {}", e.getCode(), e.getMessage(), e);
        return respond(statusOf(e.getKind()), e.getCode(), e.getMessage() != null ? e.getMessage() : e.getCode());
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

    /** 본문 파싱 실패. 예외 문구에 메서드 시그니처 · 대상 클래스 이름이 들어가므로 고정 문구만 내보내고 원문은 로그에 남긴다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽지 못함: {}", e.getMessage());
        return badRequest(UNREADABLE_BODY_MESSAGE);
    }

    /** 경로 · 쿼리 값의 형 변환 실패. 예외 문구에 java.lang.String 같은 형 이름이 들어가므로 파라미터 이름만 싣는다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        log.debug("요청 값 형 변환 실패: {}", e.getMessage());
        return badRequest("'" + e.getName() + "' 값의 형식이 올바르지 않습니다");
    }

    /**
     * 도메인 불변식 위반. 호출자 입력 탓일 수도 있고 서버 변환 버그(예: AI 제안 → 미션 변환)일 수도 있어
     * 400 으로 돌리되 스택과 함께 WARN 으로 남긴다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException 을 400 으로 돌림", e);
        return badRequest(e.getMessage() != null ? e.getMessage() : BAD_REQUEST_MESSAGE);
    }

    /**
     * Bean Validation 위반. 엔티티 저장 전 검증에서 나면 예외 문구에 엔티티 클래스 이름이 들어가므로
     * 위반마다 마지막 속성 이름과 검증 문구만 싣는다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException e) {
        log.warn("ConstraintViolationException 을 400 으로 돌림", e);
        String detail = e.getConstraintViolations().stream()
                .map(it -> leafOf(it.getPropertyPath()) + ": " + it.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(detail.isBlank() ? BAD_REQUEST_MESSAGE : detail);
    }

    /**
     * 유니크 제약 위반은 동시 요청이 사전 중복 검사를 함께 지나친 경우라 409 CONFLICT 로 보낸다.
     * NOT NULL · FK · 형식 오류도 같은 예외로 오지만 그건 서버 버그라 500 으로 둔다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException e) {
        if (!isUniqueViolation(e)) return unexpected(e);
        log.warn("유니크 제약 위반을 409 로 돌림: {}", e.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "이미 같은 데이터가 있습니다");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> unauthenticated(AuthenticationException e) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(AccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다");
    }

    /**
     * 나머지 전부. Spring MVC 표준 예외(404 · 405 · 406 · 413 · 415 · 필수 헤더 · 파라미터 누락 등)는
     * {@link ErrorResponse} 를 구현하므로 그 상태를 그대로 쓴다. ErrorResponse 는 인터페이스라
     * {@code @ExceptionHandler} 에 걸 수 없어 여기서 가른다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse standard) return standard(standard, e);
        if (e instanceof MultipartException) {
            log.debug("multipart 요청을 읽지 못함: {}", e.getMessage());
            return badRequest("multipart 요청을 읽을 수 없습니다");
        }
        log.error("처리되지 않은 예외", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", SERVER_ERROR_MESSAGE);
    }

    private ResponseEntity<ApiError> standard(ErrorResponse standard, Exception e) {
        HttpStatusCode status = standard.getStatusCode();
        if (status.is5xxServerError()) {
            if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) log.warn("일시적으로 처리할 수 없는 요청", e);
            else log.error("처리되지 않은 예외", e);
            return respond(status, standard.getHeaders(), codeOf(status), SERVER_ERROR_MESSAGE);
        }
        log.debug("표준 요청 오류 {}: {}", status.value(), e.getMessage());
        String detail = standard.getBody().getDetail();
        return respond(status, standard.getHeaders(), codeOf(status), detail != null ? detail : reasonOf(status));
    }

    private ResponseEntity<ApiError> badRequest(String message) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private static ResponseEntity<ApiError> respond(HttpStatusCode status, String code, String message) {
        return respond(status, HttpHeaders.EMPTY, code, message);
    }

    private static ResponseEntity<ApiError> respond(
            HttpStatusCode status, HttpHeaders headers, String code, String message) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message));
    }

    /**
     * 표준 HTTP 상태 → `error.code`. 이미 쓰는 이름이 있으면 그것(503 TEMPORARILY_UNAVAILABLE · 그 밖 5xx INTERNAL_ERROR),
     * 없으면 RFC 9110 상태 이름(415 UNSUPPORTED_MEDIA_TYPE · 413 CONTENT_TOO_LARGE 등)을 쓴다.
     * 400 · 401 · 403 · 404 · 405 · 409 는 기존 이름과 상태 이름이 같다.
     */
    public static String codeOf(HttpStatusCode status) {
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) return "TEMPORARILY_UNAVAILABLE";
        if (status.is5xxServerError()) return "INTERNAL_ERROR";
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "BAD_REQUEST";
    }

    static String reasonOf(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : BAD_REQUEST_MESSAGE;
    }

    /** 원인 사슬 어딘가에 SQLSTATE 23505 가 있으면 유니크 위반이다. JDBC 직접 호출 · JPA flush 어느 쪽에서 와도 같다. */
    private static boolean isUniqueViolation(Throwable e) {
        for (@Nullable Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) return true;
        }
        return false;
    }

    private static String leafOf(Path path) {
        String leaf = path.toString();
        for (Path.Node node : path) {
            if (node.getName() != null) leaf = node.getName();
        }
        return leaf;
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
