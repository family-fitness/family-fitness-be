package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 심사용 계정 로그인(`POST /api/v1/auth/review-login`). test 프로필은 꺼 두므로 여기서만 켠다.
 * 꺼져 있을 때 404 는 {@link ReviewLoginDisabledTest}.
 */
@SpringBootTest(properties = "app.auth.review-login.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ReviewLoginApiTest {
    /** IP 한도가 시험끼리 섞이지 않게 부를 때마다 다른 IP 를 쓴다. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private record Login(UUID userId, UUID familyId, String bearer, JsonNode body) {}

    private static String freshIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions reviewLogin(String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/review-login").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }));
    }

    private Login login() throws Exception {
        JsonNode body = read(reviewLogin(freshIp()).andExpect(status().isOk()));
        return new Login(
                UUID.fromString(body.get("userId").asString()),
                UUID.fromString(body.path("profiles").get(0).get("familyId").asString()),
                "Bearer " + body.get("accessToken").asString(),
                body);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(Login login, String path) throws Exception {
        return read(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, login.bearer()))
                .andExpect(status().isOk()));
    }

    private Map<String, JsonNode> members(Login login) throws Exception {
        JsonNode family = getJson(login, "/api/v1/families/" + login.familyId() + "/profiles");
        assertThat(family.get("familyName").asString()).isEqualTo("체험 가족");
        return StreamSupport.stream(family.get("profiles").spliterator(), false)
                .collect(Collectors.toMap(it -> it.get("name").asString(), Function.identity()));
    }

    @Test
    @DisplayName("로그의 IP 는 끝자리를 가린 값만 남는다 — 배포 점검에는 앞 세 옥텟(IPv6 는 /56)으로 충분하다")
    void 로그의_IP_는_끝자리를_가린_값만_남는다(CapturedOutput output) throws Exception {
        reviewLogin("198.51.100.77").andExpect(status().isOk());

        assertThat(output.getOut()).contains("심사용 계정 로그인: IP 198.51.100.* ").doesNotContain("198.51.100.77");
    }

    @Test
    @DisplayName("부를 때마다 새 계정과 새 체험 가족이 생기고, 로그인한 보호자는 곧바로 홈으로 간다")
    void 부를_때마다_새_계정과_새_체험_가족이_생긴다() throws Exception {
        Login first = login();
        Login second = login();

        assertThat(second.userId()).isNotEqualTo(first.userId());
        assertThat(second.familyId()).isNotEqualTo(first.familyId());
        for (Login each : List.of(first, second)) {
            JsonNode body = each.body();
            assertThat(body.get("refreshToken").asString()).isNotBlank();
            assertThat(body.get("nextStep").asString()).isEqualTo("HOME");
            assertThat(body.get("profiles")).hasSize(1);
            JsonNode self = body.get("profiles").get(0);
            assertThat(self.get("name").asString()).isEqualTo("엄마");
            assertThat(self.get("role").asString()).isEqualTo("PARENT");
            assertThat(self.get("sex").asString()).isEqualTo("F");
            assertThat(self.get("supportMode").asString()).isEqualTo("FULL");
            assertThat(body.get("selfProfileId").asString())
                    .isEqualTo(self.get("profileId").asString());

            Map<String, Object> user =
                    jdbc.queryForMap("select provider, provider_user_id from users where id = ?", each.userId());
            assertThat(user.get("provider")).isEqualTo("REVIEW");
            assertThat((String) user.get("provider_user_id")).startsWith("review-");
        }
        // /me 로 다시 물어도 같은 판단이다(리프레시 · 새로 고침 뒤에도 홈)
        JsonNode me = getJson(first, "/api/v1/me");
        assertThat(me.get("nextStep").asString()).isEqualTo("HOME");
    }

    @Test
    @DisplayName("체험 가족은 엄마, 아빠, 하윤(만 11세), 서준(만 6세)이고, 아이 둘은 동의가 있고 운동할 수 있는 시간이 적혀 있다")
    void 체험_가족의_식구() throws Exception {
        Login login = login();
        Map<String, JsonNode> members = members(login);

        assertThat(members).containsOnlyKeys("엄마", "아빠", "하윤", "서준");
        JsonNode dad = members.get("아빠");
        assertThat(dad.get("role").asString()).isEqualTo("PARENT");
        assertThat(dad.get("sex").asString()).isEqualTo("M");
        assertThat(dad.get("hasAccount").asBoolean()).isFalse();

        JsonNode hayun = members.get("하윤");
        assertThat(hayun.get("role").asString()).isEqualTo("CHILD");
        assertThat(hayun.get("sex").asString()).isEqualTo("F");
        assertThat(hayun.get("ageGroup").asString()).isEqualTo("유소년");
        assertThat(hayun.get("hasAccount").asBoolean()).isFalse();
        assertThat(hayun.get("consentRequired").asBoolean()).isTrue();
        assertThat(hayun.get("consentGiven").asBoolean()).isTrue();
        assertThat(hayun.get("measurable").asBoolean()).isTrue();

        JsonNode seojun = members.get("서준");
        assertThat(seojun.get("sex").asString()).isEqualTo("M");
        assertThat(seojun.get("ageGroup").asString()).isEqualTo("유아기");
        assertThat(seojun.get("consentGiven").asBoolean()).isTrue();

        for (JsonNode member : members.values()) {
            JsonNode slots =
                    getJson(login, "/api/v1/profiles/" + member.get("profileId").asString() + "/availability");
            assertThat(slots.isEmpty()).as(member.get("name").asString()).isFalse();
            // FE 편성 · 일정 화면이 고를 수 있는 분(10 · 20 · 30 · 40)만 적는다 — 15분이면 화면이 10분 칩을 켠다
            assertThat(StreamSupport.stream(slots.get("slots").spliterator(), false)
                            .map(it -> it.get("minutes").asInt())
                            .toList())
                    .as(member.get("name").asString())
                    .isNotEmpty()
                    .allSatisfy(it -> assertThat(it).isIn(10, 20, 30, 40));
        }
        assertThat(jdbc.queryForObject(
                        "select count(*) from missions where family_id = ?", Integer.class, login.familyId()))
                .isPositive(); // 지난 2주 기록은 ReviewFamilyHistoryApiTest
    }

    @Test
    @DisplayName("체험 가족의 식구 차례는 부를 때마다 같다 — 엄마 · 아빠 · 하윤 · 서준")
    void 식구_차례는_늘_같다() throws Exception {
        for (int i = 0; i < 5; i++) {
            Login login = login();
            JsonNode family = getJson(login, "/api/v1/families/" + login.familyId() + "/profiles");

            assertThat(StreamSupport.stream(family.get("profiles").spliterator(), false)
                            .map(it -> it.get("name").asString())
                            .toList())
                    .containsExactly("엄마", "아빠", "하윤", "서준");
        }
    }

    @Test
    @DisplayName("하윤은 사흘 전에 유소년 일곱 종목과 키 · 몸무게 · 허리둘레를 재서 종합 등급 2등급이 나온다")
    void 하윤은_종합_등급이_나온다() throws Exception {
        Login login = login();
        String hayun = members(login).get("하윤").get("profileId").asString();

        JsonNode latest = getJson(login, "/api/v1/profiles/" + hayun + "/fitness-tests/latest");

        assertThat(latest.get("testedOn").asString())
                .isEqualTo(today.minusDays(3).toString());
        assertThat(latest.get("heightCm").decimalValue()).isEqualByComparingTo("148.0");
        assertThat(latest.get("weightKg").decimalValue()).isEqualByComparingTo("40.0");
        assertThat(latest.get("waistCm").decimalValue()).isEqualByComparingTo("62.0");
        assertThat(StreamSupport.stream(latest.get("items").spliterator(), false)
                        .map(it -> it.get("itemCode").asString())
                        .toList())
                .containsExactlyInAnyOrder("009", "012", "020", "022", "028", "043", "044");
        JsonNode certification = latest.get("certification");
        assertThat(certification.get("status").asString()).isEqualTo("GRADED");
        assertThat(certification.get("grade").asString()).isEqualTo("2등급");
    }

    @Test
    @DisplayName("서준은 사흘 전에 유아기 종목 몇 가지를 재 둬서 측정 기록이 있다")
    void 서준은_측정_기록이_있다() throws Exception {
        Login login = login();
        String seojun = members(login).get("서준").get("profileId").asString();

        JsonNode latest = getJson(login, "/api/v1/profiles/" + seojun + "/fitness-tests/latest");

        assertThat(latest.get("fitnessTestId").isNull()).isFalse();
        assertThat(latest.get("testedOn").asString())
                .isEqualTo(today.minusDays(3).toString());
        assertThat(latest.get("items")).hasSize(4);
        assertThat(latest.get("certification").get("status").asString()).isEqualTo("NEEDS_ITEMS");
    }

    @Test
    @DisplayName("같은 IP 에서 한 시간에 60번을 넘기면 429 TOO_MANY 이고 계정을 만들지 않는다 — 다른 IP 는 그대로 된다")
    void 같은_IP_에서_60번을_넘기면_429() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 60; i++) {
            reviewLogin(ip).andExpect(status().isOk());
        }
        Integer before = jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class);

        reviewLogin(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY"));

        assertThat(jdbc.queryForObject("select count(*) from users where provider = 'REVIEW'", Integer.class))
                .isEqualTo(before);
        reviewLogin(freshIp()).andExpect(status().isOk());
    }
}
