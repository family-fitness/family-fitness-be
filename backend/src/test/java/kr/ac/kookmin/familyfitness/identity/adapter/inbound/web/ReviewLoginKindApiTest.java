package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import kr.ac.kookmin.familyfitness.identity.api.AccountQuery;
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
 * 심사용 계정 로그인의 kind 세 가지(본문 {@code {"kind": "FAMILY" | "FRESH" | "INVITED"}}). 로컬 개발용 로그인 상자의 세 계정(은영 · 가족
 * 3명 / 새 계정 · 가족 없음 / 초대받은 계정)과 같은 흐름을 심사위원도 직접 해 볼 수 있게 한다. 체험 가족의 식구 · 측정은 {@link ReviewLoginApiTest}.
 */
@SpringBootTest(properties = "app.auth.review-login.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewLoginKindApiTest {
    /** IP 한도가 시험끼리 섞이지 않게 부를 때마다 다른 IP 를 쓴다. ReviewLoginApiTest 와 다른 대역이다. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountQuery accounts;

    private record Login(UUID userId, String bearer, JsonNode body) {}

    private static String freshIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.40." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions reviewLogin(String ip, @Nullable String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/review-login")
                .with(it -> {
                    it.setRemoteAddr(ip);
                    return it;
                });
        if (body != null)
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private Login login(String kind) throws Exception {
        return login(freshIp(), "{\"kind\":\"" + kind + "\"}");
    }

    private Login login(String ip, @Nullable String body) throws Exception {
        JsonNode node = read(reviewLogin(ip, body).andExpect(status().isOk()));
        return new Login(
                UUID.fromString(node.get("userId").asString()),
                "Bearer " + node.get("accessToken").asString(),
                node);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions as(Login login, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, login.bearer()));
    }

    private ResultActions postJson(Login login, String path, Object body) throws Exception {
        return as(login, post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private String providerOf(UUID userId) {
        return jdbc.queryForObject("select provider from users where id = ?", String.class, userId);
    }

    @Test
    @DisplayName("본문 없이 부르면 FAMILY 와 같다 — 꾸며 둔 체험 가족의 보호자로 홈에 간다(예전 FE 도 그대로 된다)")
    void 본문이_없으면_FAMILY() throws Exception {
        for (String body : new String[] {null, "{}", "{\"kind\":null}"}) {
            Login login = login(freshIp(), body);

            assertThat(login.body().get("nextStep").asString()).as(body).isEqualTo("HOME");
            assertThat(login.body().get("profiles")).as(body).hasSize(1);
            assertThat(login.body().get("profiles").get(0).get("name").asString())
                    .as(body)
                    .isEqualTo("엄마");
            assertThat(login.body().has("inviteCode")).as(body).isTrue();
            assertThat(login.body().get("inviteCode").isNull()).as(body).isTrue();
        }
    }

    @Test
    @DisplayName("FAMILY — 꾸며 둔 체험 가족의 보호자, nextStep HOME, inviteCode null")
    void FAMILY_는_체험_가족의_보호자() throws Exception {
        Login login = login("FAMILY");

        assertThat(login.body().get("nextStep").asString()).isEqualTo("HOME");
        assertThat(login.body().get("profiles").get(0).get("name").asString()).isEqualTo("엄마");
        assertThat(login.body().get("inviteCode").isNull()).isTrue();
        assertThat(providerOf(login.userId())).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("모르는 kind 는 400 BAD_REQUEST 이고 계정을 만들지 않는다")
    void 모르는_kind_는_400() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class);

        for (String body : new String[] {"{\"kind\":\"GUEST\"}", "{\"kind\":\"fresh\"}", "{\"kind\":3}"}) {
            reviewLogin(freshIp(), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }

        assertThat(jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("FRESH — 가족 없는 새 심사용 계정(nextStep CREATE_FAMILY). 평소처럼 가족을 만들고 아이를 등록하고, 그 가족은 체험 리그 방을 받는다")
    void FRESH_는_가족_만들기부터_한다() throws Exception {
        Login login = login("FRESH");

        assertThat(login.body().get("nextStep").asString()).isEqualTo("CREATE_FAMILY");
        assertThat(login.body().get("profiles")).isEmpty();
        assertThat(login.body().get("selfProfileId").isNull()).isTrue();
        assertThat(login.body().get("inviteCode").isNull()).isTrue();
        assertThat(providerOf(login.userId())).isEqualTo("REVIEW");
        assertThat(accounts.isReviewAccount(login.userId())).isTrue();

        JsonNode created = read(postJson(
                        login,
                        "/api/v1/families",
                        Map.of(
                                "familyName",
                                "우리 집",
                                "owner",
                                Map.of("name", "심사 보호자", "birthDate", "1985-05-01", "sex", "F")))
                .andExpect(status().isCreated()));
        String familyId = created.get("familyId").asString();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        postJson(
                        login,
                        "/api/v1/families/" + familyId + "/profiles",
                        Map.of(
                                "name",
                                "아이",
                                "birthDate",
                                today.minusYears(9).toString(),
                                "sex",
                                "M",
                                "role",
                                "CHILD",
                                "guardianConsent",
                                Map.of("personalData", true, "healthData", true)))
                .andExpect(status().isCreated());

        JsonNode me = read(as(login, get("/api/v1/me")).andExpect(status().isOk()));
        assertThat(me.get("nextStep").asString()).isEqualTo("HOME");
        assertTrialLeague(login, familyId);
    }

    @Test
    @DisplayName("INVITED — 가짜 보호자가 꾸민 체험 가족의 아빠 자리 초대코드를 받는다(nextStep CLAIM). 코드로 합류하면 그 체험 가족의 보호자가 되고 체험 리그 방을 받는다")
    void INVITED_는_초대코드로_체험_가족에_합류한다() throws Exception {
        Login login = login("INVITED");

        assertThat(login.body().get("nextStep").asString()).isEqualTo("CLAIM");
        assertThat(login.body().get("profiles")).isEmpty();
        String code = login.body().get("inviteCode").asString();
        assertThat(code).isNotBlank();
        assertThat(providerOf(login.userId())).isEqualTo("REVIEW");
        assertThat(accounts.isReviewAccount(login.userId())).isTrue();

        // 초대코드를 낸 가짜 보호자는 심사위원이 아니다 — 심사위원 계정은 아직 어느 가족에도 없다
        UUID inviter = jdbc.queryForObject(
                "select p.user_id from profiles p join profiles seat on seat.family_id = p.family_id"
                        + " where seat.claim_code = ? and p.display_name = '엄마'",
                UUID.class,
                code);
        assertThat(inviter).isNotEqualTo(login.userId());
        assertThat(providerOf(inviter)).isEqualTo("REVIEW");

        JsonNode preview = read(as(login, get("/api/v1/invites/" + code)).andExpect(status().isOk()));
        assertThat(preview.get("familyName").asString()).isEqualTo("체험 가족");
        assertThat(preview.get("profileName").asString()).isEqualTo("아빠");
        assertThat(preview.get("invitedByName").asString()).isEqualTo("엄마");

        JsonNode claimed = read(postJson(login, "/api/v1/profiles/claim", Map.of("claimCode", code))
                .andExpect(status().isOk()));
        assertThat(claimed.get("role").asString()).isEqualTo("PARENT");
        assertThat(claimed.get("nextStep").asString()).isEqualTo("SUPPORT_MODE");
        String familyId = claimed.get("familyId").asString();

        JsonNode family = read(
                as(login, get("/api/v1/families/" + familyId + "/profiles")).andExpect(status().isOk()));
        assertThat(family.get("familyName").asString()).isEqualTo("체험 가족");
        assertThat(StreamSupport.stream(family.get("profiles").spliterator(), false)
                        .map(it -> it.get("name").asString())
                        .toList())
                .containsExactly("엄마", "아빠", "하윤", "서준");
        JsonNode me = read(as(login, get("/api/v1/me")).andExpect(status().isOk()));
        assertThat(me.get("profiles").get(0).get("name").asString()).isEqualTo("아빠");
        assertThat(me.get("profiles").get(0).get("role").asString()).isEqualTo("PARENT");
        assertThat(jdbc.queryForObject(
                        "select count(*) from missions where family_id = ?", Integer.class, UUID.fromString(familyId)))
                .as("가짜 보호자가 꾸민 체험 가족에도 지난 2주 기록이 있다")
                .isPositive();
        assertTrialLeague(login, familyId);
    }

    @Test
    @DisplayName("세 kind 모두 한 IP 한도를 같이 센다 — 섞어 불러도 60번을 넘기면 429")
    void 세_kind_가_IP_한도를_합쳐_센다() throws Exception {
        String ip = freshIp();
        String[] kinds = {"FAMILY", "FRESH", "INVITED"};
        for (int i = 0; i < 60; i++) {
            reviewLogin(ip, "{\"kind\":\"" + kinds[i % 3] + "\"}").andExpect(status().isOk());
        }

        for (String kind : kinds) {
            reviewLogin(ip, "{\"kind\":\"" + kind + "\"}")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("TOO_MANY"));
        }
    }

    /** 심사용 계정이 리그를 열면 실제 방이 아니라 그 가족 + 가짜 가족 일곱의 체험 방이다(LeagueService). */
    private void assertTrialLeague(Login login, String familyId) throws Exception {
        JsonNode league =
                read(as(login, get("/api/v1/families/" + familyId + "/league")).andExpect(status().isOk()));
        assertThat(league.get("groupSize").asInt()).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                        "select count(*) from league_members where family_id = ?",
                        Integer.class,
                        UUID.fromString(familyId)))
                .as("DB 의 실제 방에 넣지 않는다")
                .isZero();
    }
}
