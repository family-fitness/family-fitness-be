package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 심사용 계정 로그인을 켜 두었어도 끝나는 날(app.auth.review-login.until)이 지났으면 꺼진 것과 같게 404 다. */
@SpringBootTest(properties = {"app.auth.review-login.enabled=true", "app.auth.review-login.until=2020-01-01"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewLoginUntilTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("끝나는 날이 지났으면 404 NOT_FOUND 이고 계정을 만들지 않는다")
    void 끝나는_날이_지났으면_404() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class);

        mvc.perform(post("/api/v1/auth/review-login").with(request -> {
                    request.setRemoteAddr("10.99.0.1");
                    return request;
                }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class))
                .isEqualTo(before);
    }
}
