package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모두 합친 한도가 찬 뒤의 심사용 로그인. 한도(기본 한 시간 300개)를 2 로 낮춘 따로 된 컨텍스트에서 본다 — 한도를 채우면 같은 컨텍스트를
 * 쓰는 다른 시험의 로그인이 새 계정을 받지 못하기 때문이다.
 */
@SpringBootTest(properties = {"app.auth.review-login.enabled=true", "app.auth.review-login.max-total=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewLoginOverflowTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID reviewLogin(String ip) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/review-login").with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.get("nextStep").asString()).isEqualTo("HOME");
        return UUID.fromString(node.get("userId").asString());
    }

    private int reviewUsers() {
        Integer count = jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("모두 합친 한도가 차면 429 가 아니라 그 IP 가 만든 계정으로 들어오고, 만든 계정이 없는 IP 는 새 계정 하나를 받는다 — 다른 IP 의 계정은 받지 않는다")
    void 모두_합친_한도가_차면_그_IP_가_만든_계정으로_들어온다() throws Exception {
        int before = reviewUsers();
        UUID first = reviewLogin("10.30.0.1");
        UUID second = reviewLogin("10.30.0.2");
        assertThat(reviewUsers()).isEqualTo(before + 2);

        assertThat(reviewLogin("10.30.0.1")).isEqualTo(first);
        assertThat(reviewLogin("10.30.0.2")).isEqualTo(second);

        UUID third = reviewLogin("10.30.0.3");
        assertThat(third).isNotIn(List.of(first, second));
        assertThat(reviewUsers()).isEqualTo(before + 3);
        for (int i = 0; i < 3; i++) {
            assertThat(reviewLogin("10.30.0.3")).isEqualTo(third);
        }
        assertThat(reviewUsers()).isEqualTo(before + 3);
    }
}
