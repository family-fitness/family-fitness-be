package kr.ac.kookmin.familyfitness.shared.web

import jakarta.validation.ConstraintViolationException
import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 모든 실패를 `{"error": {"code", "message"}}` 로 바꾼다.
 * 도메인 예외의 [ErrorKind] → HTTP 상태 매핑은 여기 한 곳에만 있다.
 */
@RestControllerAdvice
class ApiErrorHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun domain(e: DomainException): ResponseEntity<ApiError> {
        if (e.kind == ErrorKind.UNAVAILABLE) log.warn("외부 의존성 장애: {} {}", e.code, e.message, e)
        return ResponseEntity.status(statusOf(e.kind)).body(ApiError.of(e.code, e.message ?: e.code))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(e: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val detail =
            e.bindingResult.fieldErrors
                .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
                .ifBlank { e.bindingResult.allErrors.joinToString("; ") { it.defaultMessage ?: "invalid" } }
        return badRequest(detail)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HandlerMethodValidationException::class,
        ConstraintViolationException::class,
        IllegalArgumentException::class,
    )
    fun badRequest(e: Exception): ResponseEntity<ApiError> = badRequest(e.message ?: "요청 형식이 올바르지 않습니다")

    @ExceptionHandler(NoResourceFoundException::class)
    fun notFound(e: NoResourceFoundException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", "존재하지 않는 경로입니다"))

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(e: HttpRequestMethodNotSupportedException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of("METHOD_NOT_ALLOWED", e.message ?: "METHOD_NOT_ALLOWED"))

    @ExceptionHandler(AuthenticationException::class)
    fun unauthenticated(e: AuthenticationException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("UNAUTHORIZED", "인증이 필요합니다"))

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDenied(e: AccessDeniedException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("FORBIDDEN", "권한이 없습니다"))

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ApiError> {
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of("INTERNAL_ERROR", "서버 오류가 발생했습니다"))
    }

    private fun badRequest(message: String) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("BAD_REQUEST", message))

    companion object {
        fun statusOf(kind: ErrorKind): HttpStatus =
            when (kind) {
                ErrorKind.BAD_REQUEST -> HttpStatus.BAD_REQUEST
                ErrorKind.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
                ErrorKind.FORBIDDEN -> HttpStatus.FORBIDDEN
                ErrorKind.NOT_FOUND -> HttpStatus.NOT_FOUND
                ErrorKind.CONFLICT -> HttpStatus.CONFLICT
                ErrorKind.GONE -> HttpStatus.GONE
                ErrorKind.RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT
                ErrorKind.TOO_MANY -> HttpStatus.TOO_MANY_REQUESTS
                ErrorKind.UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
            }
    }
}
