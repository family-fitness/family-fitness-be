package kr.ac.kookmin.familyfitness.shared.ai

import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/**
 * `{app.ai.base-url}/v1` 의 FastAPI 를 부르는 [AiGateway] 구현(app.ai.mode=http).
 * 엔드포인트별 타임아웃·재시도(계약 §5): assessment·trajectory 3s·2회 / videos/search 4s·2회 /
 * coach/messages 10s·0회 / POST coach/runs 2s·0회 / GET coach/runs/{id} 3s.
 * 오류 봉투 `{"error":{"code","message"}}` → 409 [AiRunInProgressException] · 404 [AiRunNotFoundException] ·
 * 400 [AiBadRequestException] · 그 외와 연결 실패·타임아웃 → [AiUnavailableException](503).
 */
@Component
@ConditionalOnProperty(name = ["app.ai.mode"], havingValue = "http")
class HttpAiGateway(
    builder: RestClient.Builder,
    props: AppProperties,
    /** 읽기 타임아웃별 요청 팩토리. null 이면 빌더의 것을 그대로 쓴다(테스트의 MockRestServiceServer). */
    private val factoryFor: (Duration) -> ClientHttpRequestFactory?,
    private val retryBackoff: Duration = Duration.ofMillis(200),
) : AiGateway {
    @Autowired
    constructor(builder: RestClient.Builder, props: AppProperties) : this(builder, props, { readTimeout -> jdkFactory(readTimeout) })

    private val log = LoggerFactory.getLogger(javaClass)
    private val baseUrl = props.ai.baseUrl.trimEnd('/') + "/v1"

    private val assessmentClient = client(builder, Duration.ofSeconds(3))
    private val videoClient = client(builder, Duration.ofSeconds(4))
    private val messageClient = client(builder, Duration.ofSeconds(10))
    private val runStartClient = client(builder, Duration.ofSeconds(2))
    private val runPollClient = client(builder, Duration.ofSeconds(3))

    override fun assess(request: AssessmentRequest): AssessmentResponse =
        withRetry(2, "fitness/assessment") {
            post(
                assessmentClient,
                "/fitness/assessment",
                AiWire.ProfileBody.of(request.profile),
                AiWire.AssessmentBody::class.java,
            ).toDomain()
        }

    override fun trajectory(request: TrajectoryRequest): TrajectoryResponse =
        withRetry(2, "fitness/trajectory") {
            post(
                assessmentClient,
                "/fitness/trajectory",
                AiWire.TrajectoryRequestBody.of(request),
                AiWire.TrajectoryBody::class.java,
            ).toDomain()
        }

    override fun searchVideos(request: VideoSearchRequest): VideoSearchResponse =
        withRetry(2, "videos/search") {
            post(
                videoClient,
                "/videos/search",
                AiWire.VideoSearchRequestBody(request.ageGroup, request.fitnessFactors, request.exerciseNames, request.k),
                AiWire.VideoSearchBody::class.java,
            ).toDomain()
        }

    override fun startCoachRun(request: CoachRunRequest): CoachRunAccepted =
        post(runStartClient, "/coach/runs", AiWire.CoachRunRequestBody.of(request), AiWire.CoachRunAcceptedBody::class.java).toDomain()

    override fun getCoachRun(runId: String): CoachRunResult =
        call("GET coach/runs/$runId") {
            runPollClient
                .get()
                .uri("/coach/runs/{id}", runId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AiWire.CoachRunResultBody::class.java)
        }.toDomain()

    override fun ask(request: CoachMessageRequest): CoachMessageResponse =
        post(
            messageClient,
            "/coach/messages",
            AiWire.CoachMessageRequestBody(request.profileRef, request.ageGroup, request.question),
            AiWire.CoachMessageBody::class.java,
        ).toDomain()

    private fun <T : Any> post(
        client: RestClient,
        path: String,
        body: Any,
        type: Class<T>,
    ): T =
        call("POST $path") {
            client
                .post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(type)
        }

    private fun <T : Any> call(
        what: String,
        exchange: () -> T?,
    ): T =
        try {
            exchange() ?: throw AiUnavailableException("AI 응답이 비어 있다: $what")
        } catch (e: RestClientResponseException) {
            throw translate(what, e)
        } catch (e: ResourceAccessException) {
            throw AiUnavailableException("AI 연결 실패·타임아웃: $what — ${e.message}", e)
        }

    private fun translate(
        what: String,
        e: RestClientResponseException,
    ): DomainException {
        val envelope = runCatching { e.getResponseBodyAs(AiWire.ErrorEnvelope::class.java) }.getOrNull()?.error
        val message = envelope?.message ?: e.responseBodyAsString.take(200)
        return when (HttpStatus.resolve(e.statusCode.value())) {
            HttpStatus.CONFLICT -> AiRunInProgressException(message.ifBlank { "실행 중인 코치 실행이 있습니다" })
            HttpStatus.NOT_FOUND -> AiRunNotFoundException(what)
            HttpStatus.BAD_REQUEST -> AiBadRequestException("AI 400 ($what): ${envelope?.code ?: ""} $message".trim())
            else -> AiUnavailableException("AI ${e.statusCode.value()} ($what): $message", e)
        }
    }

    /** [AiUnavailableException] 에만 지수 백오프로 재시도한다. 400·404·409 는 다시 보내도 같다. */
    private fun <T> withRetry(
        retries: Int,
        what: String,
        block: () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: AiUnavailableException) {
                if (attempt >= retries) throw e
                val sleep = retryBackoff.multipliedBy(1L shl attempt)
                log.warn("AI 재시도 {}/{} ({}) after {}ms: {}", attempt + 1, retries, what, sleep.toMillis(), e.message)
                if (!sleep.isZero) Thread.sleep(sleep.toMillis())
                attempt++
            }
        }
    }

    private fun client(
        builder: RestClient.Builder,
        readTimeout: Duration,
    ): RestClient {
        val b = builder.clone().baseUrl(baseUrl)
        factoryFor(readTimeout)?.let { b.requestFactory(it) }
        return b.build()
    }

    companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(1)

        /** JDK HttpClient. 연결 1초, 읽기는 엔드포인트별. */
        fun jdkFactory(readTimeout: Duration): ClientHttpRequestFactory =
            ClientHttpRequestFactoryBuilder.jdk().build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, readTimeout))
    }
}
