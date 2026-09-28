package kr.ac.kookmin.familyfitness.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공개 경로(`SecurityConfig.PUBLIC_PATHS`)는 Authorization 을 읽지 않는다.
 * 만료된 액세스 토큰이 남은 채 로그인 · 리프레시를 불러도 401 이 나지 않아야 다시 로그인할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPathBearerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JwtEncoder encoder;

    @Autowired
    AppProperties props;

    /** 서명 · 발급자 · token_use 는 맞고 만료 시각만 한 시간 지난 액세스 토큰. */
    private String expiredBearer(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.auth().jwt().issuer())
                .subject(userId.toString())
                .issuedAt(now.minus(Duration.ofHours(2)))
                .expiresAt(now.minus(Duration.ofHours(1)))
                .claim(TokenClaims.TOKEN_USE_CLAIM, TokenClaims.ACCESS)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer "
                + encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String devLoginBody() {
        return "{\"providerUserId\":\"dev-" + UUID.randomUUID() + "\"}";
    }

    @Test
    @DisplayName("만료 토큰을 실어도 개발용 로그인은 200 이다")
    void 만료_토큰을_실어도_개발용_로그인은_200_이다() throws Exception {
        mvc.perform(post("/api/v1/auth/dev-login")
                        .header(HttpHeaders.AUTHORIZATION, expiredBearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devLoginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    @DisplayName("만료 토큰을 실어도 리프레시는 본문의 리프레시 토큰으로 판단한다")
    void 만료_토큰을_실어도_리프레시는_본문의_리프레시_토큰으로_판단한다() throws Exception {
        String login = mvc.perform(post("/api/v1/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(devLoginBody()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String userId = json.readTree(login).get("userId").asString();
        String refreshToken = json.readTree(login).get("refreshToken").asString();

        mvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, expiredBearer(UUID.fromString(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    @DisplayName("만료 토큰을 실은 구글 로그인은 인증이 아니라 본문 검증에서 갈린다")
    void 만료_토큰을_실은_구글_로그인은_인증이_아니라_본문_검증에서_갈린다() throws Exception {
        mvc.perform(post("/api/v1/auth/google")
                        .header(HttpHeaders.AUTHORIZATION, expiredBearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"\",\"redirectUri\":\"https://app\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("공개 경로가 아니면 만료 토큰은 그대로 401 이다")
    void 공개_경로가_아니면_만료_토큰은_그대로_401_이다() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, expiredBearer(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
