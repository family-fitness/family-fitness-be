package kr.ac.kookmin.familyfitness.identity.adapter.outbound.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleAuthFailedException;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** 토큰 교환은 MockRestServiceServer, id_token 서명 검증은 가짜 디코더. 네트워크에 나가지 않는다. */
class GoogleOAuthAdapterTest {
    private final AppProperties props = new AppProperties(
            "Asia/Seoul",
            "http://localhost:5173",
            new AppProperties.Cors(),
            new AppProperties.Auth(
                    new AppProperties.Jwt(),
                    new AppProperties.DevLogin(),
                    new AppProperties.DevAutoLogin(),
                    new AppProperties.Google(
                            "client-id",
                            "client-secret",
                            "https://oauth2.example/token",
                            "https://www.googleapis.com/oauth2/v3/certs")),
            new AppProperties.Ai());
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();

    private @Nullable Jwt decoded = jwt("https://accounts.google.com", "client-id");
    private final JwtDecoder decoder = token -> {
        assertThat(token).isEqualTo("ID_TOKEN");
        Jwt jwt = decoded;
        if (jwt == null) throw new BadJwtException("bad signature");
        return jwt;
    };
    private final GoogleOAuthAdapter adapter = new GoogleOAuthAdapter(builder, props, decoder);

    private static Jwt jwt(String issuer, String audience) {
        return jwt(issuer, audience, "google-sub-1");
    }

    private static Jwt jwt(String issuer, String audience, String subject) {
        return Jwt.withTokenValue("ID_TOKEN")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .claim("email", "parent@example.com")
                .issuedAt(Instant.parse("2026-09-08T10:00:00Z"))
                .expiresAt(Instant.parse("2026-09-08T11:00:00Z"))
                .build();
    }

    private void tokenEndpointReturns(String body) {
        server.expect(requestTo("https://oauth2.example/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedForm()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static MultiValueMap<String, String> expectedForm() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", "AUTH_CODE");
        form.add("client_id", "client-id");
        form.add("client_secret", "client-secret");
        form.add("redirect_uri", "https://app.example.com/oauth");
        form.add("grant_type", "authorization_code");
        return form;
    }

    @Test
    @DisplayName("인가코드를 교환하고 id_token 의 sub·email 을 돌려준다")
    void 인가코드를_교환하고_id_token_의_sub_email_을_돌려준다() {
        tokenEndpointReturns("{\"access_token\":\"x\",\"id_token\":\"ID_TOKEN\",\"token_type\":\"Bearer\"}");

        GoogleIdentity identity = adapter.exchange("AUTH_CODE", "https://app.example.com/oauth");

        assertThat(identity.subject()).isEqualTo("google-sub-1");
        assertThat(identity.email()).isEqualTo("parent@example.com");
        server.verify();
    }

    @Test
    @DisplayName("accounts_google_com 발급자도 받는다")
    void accounts_google_com_발급자도_받는다() {
        decoded = jwt("accounts.google.com", "client-id");
        tokenEndpointReturns("{\"id_token\":\"ID_TOKEN\"}");

        assertThat(adapter.exchange("AUTH_CODE", "https://app.example.com/oauth")
                        .subject())
                .isEqualTo("google-sub-1");
    }

    @Test
    @DisplayName("토큰 교환이 4xx 면 GOOGLE_AUTH_FAILED")
    void 토큰_교환이_4xx_면_GOOGLE_AUTH_FAILED() {
        server.expect(requestTo("https://oauth2.example/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid_grant\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        GoogleAuthFailedException e = assertThrows(
                GoogleAuthFailedException.class, () -> adapter.exchange("AUTH_CODE", "https://app.example.com/oauth"));
        assertThat(e.getCode()).isEqualTo("GOOGLE_AUTH_FAILED");
    }

    @Test
    @DisplayName("응답에 id_token 이 없으면 실패")
    void 응답에_id_token_이_없으면_실패() {
        tokenEndpointReturns("{\"access_token\":\"x\"}");

        assertThrows(
                GoogleAuthFailedException.class, () -> adapter.exchange("AUTH_CODE", "https://app.example.com/oauth"));
    }

    @Test
    @DisplayName("서명 검증 실패·발급자 불일치·대상 불일치는 모두 실패")
    void 서명_검증_실패_발급자_불일치_대상_불일치는_모두_실패() {
        decoded = null;
        tokenEndpointReturns("{\"id_token\":\"ID_TOKEN\"}");
        assertThrows(
                GoogleAuthFailedException.class, () -> adapter.exchange("AUTH_CODE", "https://app.example.com/oauth"));

        server.reset();
        decoded = jwt("https://evil.example", "client-id");
        tokenEndpointReturns("{\"id_token\":\"ID_TOKEN\"}");
        assertThrows(
                GoogleAuthFailedException.class, () -> adapter.exchange("AUTH_CODE", "https://app.example.com/oauth"));

        server.reset();
        decoded = jwt("https://accounts.google.com", "other-client");
        tokenEndpointReturns("{\"id_token\":\"ID_TOKEN\"}");
        assertThrows(
                GoogleAuthFailedException.class, () -> adapter.exchange("AUTH_CODE", "https://app.example.com/oauth"));
    }
}
