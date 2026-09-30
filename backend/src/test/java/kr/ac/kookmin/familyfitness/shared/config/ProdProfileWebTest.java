package kr.ac.kookmin.familyfitness.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영 프로필(application-prod.properties)을 실제로 읽어 띄운 서버에서 외부에 열린 엔드포인트(주소)를 확인한다. 운영이 환경변수로 받는 값(DB 주소 ·
 * FE 주소 · JWT 비밀값)만 시험 값으로 채운다 — DB 는 이 시험만 쓰는 H2 이고 시드 없이 버전 마이그레이션만 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(
        properties = {
            "SPRING_DATASOURCE_URL=jdbc:h2:mem:familyfitness-prod;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "SPRING_DATASOURCE_USERNAME=sa",
            "SPRING_DATASOURCE_PASSWORD=",
            "APP_FRONTEND_BASE_URL=https://fit.example.com",
            "APP_JWT_SECRET=prod-test-secret-prod-test-secret-prod-test-secret-0123456789",
            "APP_AI_MODE=stub"
        })
class ProdProfileWebTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    AppProperties properties;

    @Test
    @DisplayName("운영에서 actuator 는 health 만 연다 — 로그인해도 /actuator/info · /actuator/modulith 는 404 다")
    void 운영에서_actuator_는_health_만_연다() throws Exception {
        String bearer = auth.bearer(UUID.randomUUID());

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/modulith").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("운영에서 심사용 로그인은 켜져 있고 2026-10-31 이 지나면 스스로 꺼진다(APP_AUTH_REVIEW_LOGIN_UNTIL 로 바꾼다)")
    void 운영에서_심사용_로그인은_끝나는_날이_있다() {
        assertThat(properties.auth().reviewLogin().enabled()).isTrue();
        assertThat(properties.auth().reviewLogin().until()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("운영에서 Swagger UI 와 /v3/api-docs 는 꺼져 있다 — 로그인 없이 API 전체 모양이 나가지 않는다")
    void 운영에서_Swagger_UI_와_api_docs_는_꺼져_있다() throws Exception {
        String bearer = auth.bearer(UUID.randomUUID());

        for (String path : new String[] {"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isNotFound());
        }
    }
}
