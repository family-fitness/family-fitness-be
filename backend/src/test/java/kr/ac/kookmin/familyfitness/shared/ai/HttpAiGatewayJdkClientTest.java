package kr.ac.kookmin.familyfitness.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 실제 JDK HttpClient({@link HttpAiGateway#jdkFactory})로 로컬 HTTP 서버를 불러 요청 머리를 본다.
 * JDK HttpClient 는 기본이 HTTP/2 라 평문 http 주소에도 「Upgrade: h2c」 를 붙여 보낸다. AI(uvicorn)는 h2c 를 받지 않아
 * 부를 때마다 「Unsupported upgrade request」 경고를 남겼다.
 */
class HttpAiGatewayJdkClientTest {
    private HttpServer server;
    private final AtomicReference<Headers> received = new AtomicReference<>();
    private final AtomicReference<String> protocol = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/coach/runs/cr_x", exchange -> {
            received.set(exchange.getRequestHeaders());
            protocol.set(exchange.getProtocol());
            byte[] body =
                    "{\"error\":{\"code\":\"RUN_NOT_FOUND\",\"message\":\"no\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("AI 를 HTTP/1.1 로 부른다 — 「Upgrade: h2c」 · 「HTTP2-Settings」 머리를 보내지 않는다")
    void AI_를_HTTP_1_1_로_부른다() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpAiGateway gateway = new HttpAiGateway(
                RestClient.builder(),
                new AppProperties(
                        "Asia/Seoul",
                        "http://localhost:5173",
                        new AppProperties.Cors(),
                        new AppProperties.Auth(),
                        new AppProperties.Ai("http", baseUrl)),
                HttpAiGateway::jdkFactory,
                Duration.ZERO);

        assertThrows(AiRunNotFoundException.class, () -> gateway.getCoachRun("cr_x"));

        assertThat(protocol.get()).isEqualTo("HTTP/1.1");
        assertThat(received.get()).isNotNull();
        assertThat(received.get().containsKey("Upgrade")).isFalse();
        assertThat(received.get().containsKey("HTTP2-Settings")).isFalse();
    }
}
