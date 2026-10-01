package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 심사용 로그인 IP 한도가 X-Forwarded-For 의 브라우저 IP 로 갈리는지 실제 Tomcat 으로 본다(MockMvc 는 Tomcat 의 RemoteIpValve 를
 * 거치지 않아 이 경로를 못 본다). FE 는 /api/v1/** 를 늘 Next 서버를 거쳐 넘기므로, 이 설정이 없으면 모든 심사위원이 Next 서버 IP
 * 하나로 세져 누구든 31번만 부르면 한 시간 동안 모두가 429 를 받는다.
 *
 * <p>시험은 127.0.0.1 에서 부른다. Tomcat 기본 믿을 프록시(루프백 · 사설망)라 그 요청이 실어 온 X-Forwarded-For 를 remoteAddr 로 쓴다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.auth.review-login.enabled=true", "server.forward-headers-strategy=native"})
@ActiveProfiles("test")
class ReviewLoginForwardedForTest {
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    private int reviewLogin(String forwardedFor) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v1/auth/review-login"))
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    @DisplayName("믿을 프록시(Next 서버)를 거친 요청은 X-Forwarded-For 의 브라우저 IP 마다 따로 센다 — 한 사람이 60번을 넘겨도 다른 사람은 된다")
    void 프록시를_거친_요청은_브라우저_IP_마다_센다() throws Exception {
        for (int i = 0; i < 60; i++) {
            assertThat(reviewLogin("198.51.100.1")).isEqualTo(200);
        }

        assertThat(reviewLogin("198.51.100.1")).isEqualTo(429);
        assertThat(reviewLogin("198.51.100.2")).isEqualTo(200);
        // 앞 프록시가 붙인 값 뒤에 믿을 프록시 주소가 이어져도 브라우저 IP 로 센다
        assertThat(reviewLogin("198.51.100.1, 10.0.0.5")).isEqualTo(429);
    }

    @Test
    @DisplayName("운영 설정은 X-Forwarded-For 를 읽는다(server.forward-headers-strategy=native)")
    void 운영은_X_Forwarded_For_를_읽는다() throws IOException {
        Properties prod = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application-prod.properties")) {
            assertThat(in).isNotNull();
            prod.load(in);
        }

        assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }
}
