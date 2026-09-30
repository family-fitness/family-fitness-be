package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** test 프로필은 심사용 계정 로그인을 켜지 않는다(기본값 false). 꺼져 있으면 경로가 없어 404 다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewLoginDisabledTest {
    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("app.auth.review-login.enabled 가 꺼져 있으면 404 NOT_FOUND")
    void 꺼져_있으면_404() throws Exception {
        mvc.perform(post("/api/v1/auth/review-login"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
