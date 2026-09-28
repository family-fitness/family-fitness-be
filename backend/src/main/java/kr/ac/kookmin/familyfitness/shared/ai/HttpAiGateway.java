package kr.ac.kookmin.familyfitness.shared.ai;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * `{app.ai.base-url}/v1` 의 FastAPI 를 부르는 {@link AiGateway} 구현(app.ai.mode=http).
 * 엔드포인트별 타임아웃·재시도(계약 §5): assessment·trajectory 3s·2회 / videos/search 4s·2회 /
 * coach/messages 10s·0회 / POST coach/runs 2s·0회 / GET coach/runs/{id} 3s.
 * 오류 봉투 `{"error":{"code","message"}}` → 409 {@link AiRunInProgressException} · 404 {@link AiRunNotFoundException} ·
 * 400 {@link AiBadRequestException} · 그 외와 연결 실패·타임아웃 → {@link AiUnavailableException}(503).
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "http")
public class HttpAiGateway implements AiGateway {
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);

    /** POST coach/runs 읽기 한도. 재시도하지 않는다. */
    public static final Duration RUN_START_READ_TIMEOUT = Duration.ofSeconds(2);

    /** GET coach/runs/{id} 한 번의 읽기 한도. 재시도하지 않는다. */
    public static final Duration RUN_POLL_READ_TIMEOUT = Duration.ofSeconds(3);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Function<Duration, @Nullable ClientHttpRequestFactory> factoryFor;
    private final Duration retryBackoff;
    private final String baseUrl;
    private final RestClient assessmentClient;
    private final RestClient videoClient;
    private final RestClient messageClient;
    private final RestClient runStartClient;
    private final RestClient runPollClient;

    @Autowired
    public HttpAiGateway(RestClient.Builder builder, AppProperties props) {
        this(builder, props, HttpAiGateway::jdkFactory, Duration.ofMillis(200));
    }

    /**
     * @param factoryFor 읽기 타임아웃별 요청 팩토리. null 이면 빌더의 것을 그대로 쓴다(테스트의 MockRestServiceServer).
     */
    public HttpAiGateway(
            RestClient.Builder builder,
            AppProperties props,
            Function<Duration, @Nullable ClientHttpRequestFactory> factoryFor,
            Duration retryBackoff) {
        this.factoryFor = factoryFor;
        this.retryBackoff = retryBackoff;
        this.baseUrl = trimTrailingSlashes(props.ai().baseUrl()) + "/v1";
        this.assessmentClient = client(builder, Duration.ofSeconds(3));
        this.videoClient = client(builder, Duration.ofSeconds(4));
        this.messageClient = client(builder, Duration.ofSeconds(10));
        this.runStartClient = client(builder, RUN_START_READ_TIMEOUT);
        this.runPollClient = client(builder, RUN_POLL_READ_TIMEOUT);
    }

    @Override
    public AssessmentResponse assess(AssessmentRequest request) {
        return withRetry(2, "fitness/assessment", () -> post(
                        assessmentClient,
                        "/fitness/assessment",
                        AiWire.ProfileBody.of(request.profile()),
                        AiWire.AssessmentBody.class)
                .toDomain());
    }

    @Override
    public TrajectoryResponse trajectory(TrajectoryRequest request) {
        return withRetry(2, "fitness/trajectory", () -> post(
                        assessmentClient,
                        "/fitness/trajectory",
                        AiWire.TrajectoryRequestBody.of(request),
                        AiWire.TrajectoryBody.class)
                .toDomain());
    }

    @Override
    public VideoSearchResponse searchVideos(VideoSearchRequest request) {
        return withRetry(2, "videos/search", () -> post(
                        videoClient,
                        "/videos/search",
                        new AiWire.VideoSearchRequestBody(
                                request.ageGroup(), request.fitnessFactors(), request.exerciseNames(), request.k()),
                        AiWire.VideoSearchBody.class)
                .toDomain());
    }

    @Override
    public CoachRunAccepted startCoachRun(CoachRunRequest request) {
        return post(
                        runStartClient,
                        "/coach/runs",
                        AiWire.CoachRunRequestBody.of(request),
                        AiWire.CoachRunAcceptedBody.class)
                .toDomain();
    }

    @Override
    public CoachRunResult getCoachRun(String runId) {
        return call("GET coach/runs/" + runId, () -> runPollClient
                        .get()
                        .uri("/coach/runs/{id}", runId)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(AiWire.CoachRunResultBody.class))
                .toDomain();
    }

    @Override
    public CoachMessageResponse ask(CoachMessageRequest request) {
        return post(
                        messageClient,
                        "/coach/messages",
                        new AiWire.CoachMessageRequestBody(
                                request.profileRef(), request.ageGroup(), request.question()),
                        AiWire.CoachMessageBody.class)
                .toDomain();
    }

    private <T> T post(RestClient client, String path, Object body, Class<T> type) {
        return call("POST " + path, () -> client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(type));
    }

    private <T> T call(String what, Supplier<@Nullable T> exchange) {
        T result;
        try {
            result = exchange.get();
        } catch (RestClientResponseException e) {
            throw translate(what, e);
        } catch (ResourceAccessException e) {
            throw new AiUnavailableException("AI 연결 실패·타임아웃: " + what + " — " + e.getMessage(), e);
        }
        if (result == null) throw new AiUnavailableException("AI 응답이 비어 있다: " + what);
        return result;
    }

    private DomainException translate(String what, RestClientResponseException e) {
        AiWire.ErrorEnvelope.ErrorBody envelope = errorBodyOf(e);
        String message = envelope != null && envelope.message() != null
                ? envelope.message()
                : take(e.getResponseBodyAsString(), 200);
        int status = e.getStatusCode().value();
        if (status == HttpStatus.CONFLICT.value()) {
            return new AiRunInProgressException(message.isBlank() ? "실행 중인 코치 실행이 있습니다" : message);
        }
        if (status == HttpStatus.NOT_FOUND.value()) return new AiRunNotFoundException(what);
        if (status == HttpStatus.BAD_REQUEST.value()) {
            String code = envelope != null && envelope.code() != null ? envelope.code() : "";
            return new AiBadRequestException(("AI 400 (" + what + "): " + code + " " + message).trim());
        }
        return new AiUnavailableException("AI " + status + " (" + what + "): " + message, e);
    }

    private static AiWire.ErrorEnvelope.@Nullable ErrorBody errorBodyOf(RestClientResponseException e) {
        try {
            AiWire.ErrorEnvelope envelope = e.getResponseBodyAs(AiWire.ErrorEnvelope.class);
            return envelope == null ? null : envelope.error();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** {@link AiUnavailableException} 에만 지수 백오프로 재시도한다. 400·404·409 는 다시 보내도 같다. */
    private <T> T withRetry(int retries, String what, Supplier<T> block) {
        int attempt = 0;
        while (true) {
            try {
                return block.get();
            } catch (AiUnavailableException e) {
                if (attempt >= retries) throw e;
                Duration sleep = retryBackoff.multipliedBy(1L << attempt);
                log.warn(
                        "AI 재시도 {}/{} ({}) after {}ms: {}",
                        attempt + 1,
                        retries,
                        what,
                        sleep.toMillis(),
                        e.getMessage());
                if (!sleep.isZero()) sleep(sleep.toMillis());
                attempt++;
            }
        }
    }

    private RestClient client(RestClient.Builder builder, Duration readTimeout) {
        RestClient.Builder b = builder.clone().baseUrl(baseUrl);
        ClientHttpRequestFactory factory = factoryFor.apply(readTimeout);
        if (factory != null) b.requestFactory(factory);
        return b.build();
    }

    /** JDK HttpClient. 연결 1초, 읽기는 엔드포인트별. */
    public static ClientHttpRequestFactory jdkFactory(Duration readTimeout) {
        return ClientHttpRequestFactoryBuilder.jdk()
                .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, readTimeout));
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    private static String take(String value, int n) {
        return value.length() <= n ? value : value.substring(0, n);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
