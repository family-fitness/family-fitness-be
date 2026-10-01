package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가족 초대(초대 먼저). 보호자가 역할만 정해 코드를 내고, 코드로 들어온 사람이 이름과 생년월일을 넣어 자기 프로필을 만든다.
 * H2(PostgreSQL 모드) + Flyway + 실제 보안 필터를 거친다. 계정은 개발용 로그인으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FamilyInviteApiTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now();

    private record Session(UUID userId, String bearer) {}

    /** 가족을 만든 보호자와 그 가족. */
    private record Home(Session owner, String familyId, String ownerProfileId) {}

    // ===== 가족 초대 만들기 =====

    @Test
    @DisplayName("보호자가 CHILD 초대를 만들면 201 이고 코드, 역할, 7일 뒤 만료, 가족 id 를 준다. 보낸 동의와 보호자를 표에 남긴다")
    void 보호자가_CHILD_초대를_만든다() throws Exception {
        Home home = home();
        Instant before = Instant.now();

        JsonNode invite = read(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true))
                .andExpect(status().isCreated()));

        String code = invite.get("code").asString();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
        assertThat(invite.get("role").asString()).isEqualTo("CHILD");
        assertThat(invite.get("familyId").asString()).isEqualTo(home.familyId());
        assertThat(Instant.parse(invite.get("expiresAt").asString()))
                .isCloseTo(before.plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));
        Map<String, Object> row = jdbc.queryForMap("select * from family_invites where code = ?", code);
        assertThat(row.get("family_id").toString()).isEqualTo(home.familyId());
        assertThat(row.get("role")).isEqualTo("CHILD");
        assertThat(row.get("consent_personal_data")).isEqualTo(true);
        assertThat(row.get("consent_health_data")).isEqualTo(true);
        assertThat(row.get("consent_by_user_id").toString())
                .isEqualTo(home.owner().userId().toString());
        assertThat(row.get("issued_by_profile_id").toString()).isEqualTo(home.ownerProfileId());
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("claimed_by_user_id")).isNull();
    }

    @Test
    @DisplayName("PARENT 초대는 보호자 동의 없이 만든다. 같은 가족에 초대를 여러 개 내도 코드는 서로 다르다")
    void PARENT_초대는_동의_없이_만든다() throws Exception {
        Home home = home();

        JsonNode first =
                read(createInvite(home.owner(), home.familyId(), "PARENT", null).andExpect(status().isCreated()));
        JsonNode second =
                read(createInvite(home.owner(), home.familyId(), "PARENT", null).andExpect(status().isCreated()));

        assertThat(first.get("role").asString()).isEqualTo("PARENT");
        assertThat(first.get("code").asString()).isNotEqualTo(second.get("code").asString());
        Map<String, Object> row = jdbc.queryForMap(
                "select * from family_invites where code = ?", first.get("code").asString());
        assertThat(row.get("consent_personal_data")).isNull();
        assertThat(row.get("consent_health_data")).isNull();
        assertThat(row.get("consent_by_user_id")).isNull();
    }

    @Test
    @DisplayName("가족 초대는 그 가족 보호자만 만든다. 아이 계정 403 NOT_A_PARENT, 다른 가족 403 NOT_SAME_FAMILY, 없는 가족 404")
    void 가족_초대는_그_가족_보호자만_만든다() throws Exception {
        Home home = home();
        Session kid = childAccount(home);
        Session otherParent = home().owner();
        Session noFamily = devLogin();

        createInvite(kid, home.familyId(), "PARENT", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        createInvite(otherParent, home.familyId(), "PARENT", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        createInvite(noFamily, home.familyId(), "CHILD", consent(true, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        createInvite(home.owner(), UUID.randomUUID().toString(), "PARENT", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_FOUND"));
        assertThat(count("select count(*) from family_invites where family_id = ?", UUID.fromString(home.familyId())))
                .isZero();
    }

    @Test
    @DisplayName("CHILD 초대에 보호자 동의가 없거나 하나라도 false 면 422 CONSENT_REQUIRED, 역할이 없으면 400")
    void CHILD_초대에_동의가_없으면_422() throws Exception {
        Home home = home();

        createInvite(home.owner(), home.familyId(), "CHILD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        createInvite(home.owner(), home.familyId(), "CHILD", consent(true, false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        mvc.perform(json(auth(post("/api/v1/families/" + home.familyId() + "/invites"), home.owner()), Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(json(
                        auth(post("/api/v1/families/" + home.familyId() + "/invites"), home.owner()),
                        Map.of("role", "GRANDMA")))
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from family_invites where family_id = ?", UUID.fromString(home.familyId())))
                .isZero();
    }

    // ===== 도우미 =====

    private Session devLogin() throws Exception {
        String subject = "invite-" + UUID.randomUUID();
        JsonNode body = read(mvc.perform(json(post("/api/v1/auth/dev-login"), Map.of("providerUserId", subject)))
                .andExpect(status().isOk()));
        return new Session(
                UUID.fromString(body.get("userId").asString()),
                "Bearer " + body.get("accessToken").asString());
    }

    /** 새 계정이 「우리 가족」 을 만든다. 오너는 1988년생 엄마다. */
    private Home home() throws Exception {
        Session owner = devLogin();
        JsonNode created = read(mvc.perform(json(
                        auth(post("/api/v1/families"), owner),
                        Map.of(
                                "familyName",
                                "우리 가족",
                                "owner",
                                Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "F"))))
                .andExpect(status().isCreated()));
        return new Home(
                owner,
                created.get("familyId").asString(),
                created.get("ownerProfile").get("profileId").asString());
    }

    /** 보호자가 정보를 넣어 만든 아이 자리에 자리 초대코드로 붙은 아이 계정. */
    private Session childAccount(Home home) throws Exception {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("name", "첫째");
        member.put("birthDate", today.minusYears(10).toString());
        member.put("sex", "M");
        member.put("role", "CHILD");
        member.put("guardianConsent", consent(true, true));
        String childId = read(mvc.perform(json(
                                auth(post("/api/v1/families/" + home.familyId() + "/profiles"), home.owner()), member))
                        .andExpect(status().isCreated()))
                .get("profileId")
                .asString();
        String code = read(mvc.perform(auth(post("/api/v1/profiles/" + childId + "/invite"), home.owner()))
                        .andExpect(status().isCreated()))
                .get("claimCode")
                .asString();
        Session kid = devLogin();
        mvc.perform(json(auth(post("/api/v1/profiles/claim"), kid), Map.of("claimCode", code)))
                .andExpect(status().isOk());
        return kid;
    }

    private ResultActions createInvite(
            Session session, String familyId, String role, @Nullable Map<String, Object> guardianConsent)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", role);
        if (guardianConsent != null) body.put("guardianConsent", guardianConsent);
        return mvc.perform(json(auth(post("/api/v1/families/" + familyId + "/invites"), session), body));
    }

    private static Map<String, Object> consent(boolean personal, boolean health) {
        return Map.of("personalData", personal, "healthData", health);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, Session session) {
        return builder.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    private JsonNode read(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }
}
