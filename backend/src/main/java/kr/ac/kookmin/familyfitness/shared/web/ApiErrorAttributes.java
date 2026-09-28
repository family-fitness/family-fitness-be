package kr.ac.kookmin.familyfitness.shared.web;

import jakarta.servlet.RequestDispatcher;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

/**
 * /error 경로의 본문도 `{"error": {"code", "message"}}` 로 바꾼다.
 * 이 경로로 오는 것: 필터 밖으로 던져진 예외, Spring Security 방화벽 거절(400), 컨테이너의 sendError.
 * 상태 코드는 Boot 의 BasicErrorController 가 요청 속성 {@link RequestDispatcher#ERROR_STATUS_CODE} 로 정하고,
 * 여기서는 같은 값으로 본문만 만든다. 예외 문구는 싣지 않는다 — 필터 예외 문구에 내부 정보가 들어갈 수 있다.
 */
@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {
    @Override
    public Map<String, @Nullable Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        HttpStatusCode status = statusOf(webRequest);
        String message =
                status.is5xxServerError() ? ApiErrorHandler.SERVER_ERROR_MESSAGE : ApiErrorHandler.reasonOf(status);
        Map<String, @Nullable Object> body = new LinkedHashMap<>();
        body.put("error", new ApiError.Body(ApiErrorHandler.codeOf(status), message));
        return body;
    }

    /** BasicErrorController(AbstractErrorController.getStatus)와 같은 규칙: 속성이 없거나 모르는 값이면 500. */
    private static HttpStatusCode statusOf(WebRequest webRequest) {
        Object code = webRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
