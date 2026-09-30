package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모두 합친 한도가 찬 뒤 kind 세 가지의 심사용 로그인. 한도를 3 으로 낮춘 따로 된 컨텍스트에서 본다 — {@link ReviewLoginOverflowTest}
 * (한도 2)와 한도를 나눠 쓰지 않게.
 */
@SpringBootTest(properties = {"app.auth.review-login.enabled=true", "app.auth.review-login.max-total=3"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewLoginKindOverflowTest {
    private static final String IP = "10.50.0.1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode reviewLogin(String kind) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/review-login")
                        .with(request -> {
                            request.setRemoteAddr(IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"" + kind + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private int reviewUsers() {
        Integer count = jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("한도가 차면 그 IP 가 만든 같은 kind 의 최근 계정을 다시 준다 — 초대받은 계정은 같은 초대코드로 다시 CLAIM, 합류한 뒤에는 그 가족으로")
    void 한도가_차면_같은_kind_의_계정을_다시_준다() throws Exception {
        JsonNode family = reviewLogin("FAMILY");
        JsonNode fresh = reviewLogin("FRESH");
        JsonNode invited = reviewLogin("INVITED");
        int made = reviewUsers();

        JsonNode familyAgain = reviewLogin("FAMILY");
        assertThat(familyAgain.get("userId")).isEqualTo(family.get("userId"));
        assertThat(familyAgain.get("nextStep").asString()).isEqualTo("HOME");

        JsonNode freshAgain = reviewLogin("FRESH");
        assertThat(freshAgain.get("userId")).isEqualTo(fresh.get("userId"));
        assertThat(freshAgain.get("nextStep").asString()).isEqualTo("CREATE_FAMILY");

        JsonNode invitedAgain = reviewLogin("INVITED");
        assertThat(invitedAgain.get("userId")).isEqualTo(invited.get("userId"));
        assertThat(invitedAgain.get("nextStep").asString()).isEqualTo("CLAIM");
        assertThat(invitedAgain.get("inviteCode").asString())
                .isEqualTo(invited.get("inviteCode").asString());
        assertThat(reviewUsers()).as("새 계정을 만들지 않는다").isEqualTo(made);

        String code = invited.get("inviteCode").asString();
        mvc.perform(post("/api/v1/profiles/claim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + invited.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("claimCode", code))))
                .andExpect(status().isOk());

        JsonNode afterClaim = reviewLogin("INVITED");
        assertThat(afterClaim.get("userId")).isEqualTo(invited.get("userId"));
        assertThat(afterClaim.get("nextStep").asString()).isEqualTo("SUPPORT_MODE");
        assertThat(afterClaim.get("profiles").get(0).get("name").asString()).isEqualTo("아빠");
        assertThat(afterClaim.get("inviteCode").isNull()).as("이미 쓴 코드는 주지 않는다").isTrue();
    }
}
