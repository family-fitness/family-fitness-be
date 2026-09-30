package kr.ac.kookmin.familyfitness.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunExecutorConfig;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.security.TokenClaims;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 여정 D — 할머니(63)가 만든 가족에 제 폰을 쓰는 지아(15)가 초대코드로 들어온다.
 * 지아 계정으로는 몸무게 · 백분위 · 등급 · 체지방률 · 허리둘레가 비어 있어야 하고, 만 14세 이상이라 보호자 동의 없이 더한다.
 * 지아가 운동을 끝내고 「알리기」 → 할머니가 스티커 → 지아가 「고마워요」.
 * 시계는 움직일 수 있는 시계로 바꾸고 AI 는 스텁, 이 시험만 쓰는 인메모리 DB 에서 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-journey-teen;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.coach.poll-interval-ms=0",
            "app.coach.max-polls=3"
        })
class TeenJourneyTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate D1 = LocalDate.of(2026, 9, 30);
    private static final MovableClock CLOCK =
            new MovableClock(D1.atTime(6, 0).atZone(SEOUL).toInstant());

    @TestBean(name = "clock", methodName = "journeyClock")
    Clock clock;

    static Clock journeyClock() {
        return CLOCK;
    }

    @TestBean(name = CoachRunExecutorConfig.EXECUTOR, methodName = "syncCoachRunExecutor")
    TaskExecutor coachRunTaskExecutor;

    static TaskExecutor syncCoachRunExecutor() {
        return new SyncTaskExecutor();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AppProperties props;

    @Test
    @DisplayName("만 15세는 보호자 동의 없이 더하고, 초대코드로 제 계정에 붙인다")
    void 만_15세는_동의_없이_더하고_초대코드로_제_계정에_붙인다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Family f = newFamily();

        String jia = send(get("/api/v1/families/" + f.familyId + "/profiles"), f.grandma, null, 200);
        List<Map<String, Object>> rows = JsonPath.read(jia, "$.profiles[?(@.profileId == '" + f.jiaId + "')]");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("role", "CHILD")
                .containsEntry("ageGroup", "청소년")
                .containsEntry("consentRequired", false)
                .containsEntry("consentGiven", true)
                .containsEntry("measurable", true)
                .containsEntry("hasAccount", true);
    }

    @Test
    @DisplayName("지아 계정으로 결과를 보면 몸무게 · 백분위 · 등급 · 체지방률 · 허리둘레가 비어 있고, 할머니 계정으로는 다 보인다")
    void 지아_계정으로_보면_몸무게_백분위_등급이_숨겨진다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Family f = newFamily();
        measureJia(f);

        String mine = send(get("/api/v1/profiles/" + f.jiaId + "/fitness-tests/latest"), f.jia, null, 200);
        assertThat((Object) JsonPath.read(mine, "$.heightCm")).isNotNull();
        assertThat((Object) JsonPath.read(mine, "$.weightKg")).isNull();
        assertThat((Object) JsonPath.read(mine, "$.bodyFatPct")).isNull();
        assertThat((Object) JsonPath.read(mine, "$.waistCm")).isNull();
        assertThat((Object) JsonPath.read(mine, "$.certification")).isNull();
        assertThat((Object) JsonPath.read(mine, "$.weakest")).isNull();
        assertThat((Object) JsonPath.read(mine, "$.strongest")).isNull();
        assertThat((Object) JsonPath.read(mine, "$.coachDirection")).isNull();
        List<Object> itemPercentiles = JsonPath.read(mine, "$.items[*].percentile");
        assertThat(itemPercentiles).isNotEmpty().containsOnlyNulls();
        List<Object> topText = JsonPath.read(mine, "$.items[*].topPercentText");
        assertThat(topText).containsOnlyNulls();
        List<Object> radar = JsonPath.read(mine, "$.radar[*].percentile");
        assertThat(radar).containsOnlyNulls();
        List<Object> values = JsonPath.read(mine, "$.items[*].value");
        assertThat(values).doesNotContainNull();

        String history = send(get("/api/v1/profiles/" + f.jiaId + "/fitness-tests"), f.jia, null, 200);
        List<Object> weights = JsonPath.read(history, "$.tests[*].weightKg");
        assertThat(weights).hasSize(1).containsOnlyNulls();

        String map = send(get("/api/v1/families/" + f.familyId + "/fitness-map"), f.jia, null, 200);
        List<Object> headline = JsonPath.read(map, "$.members[?(@.profileId == '" + f.jiaId + "')].headline");
        assertThat(headline).containsOnlyNulls();

        String grandma = send(get("/api/v1/profiles/" + f.jiaId + "/fitness-tests/latest"), f.grandma, null, 200);
        assertThat(((Number) JsonPath.read(grandma, "$.weightKg")).doubleValue())
                .isEqualTo(50.0);
        assertThat(((Number) JsonPath.read(grandma, "$.bodyFatPct")).doubleValue())
                .isEqualTo(24.0);
        assertThat(((Number) JsonPath.read(grandma, "$.waistCm")).doubleValue()).isEqualTo(66.0);
        assertThat((Object) JsonPath.read(grandma, "$.certification")).isNotNull();
        List<Object> grandmaPercentiles = JsonPath.read(grandma, "$.items[*].percentile");
        assertThat(grandmaPercentiles).doesNotContainNull();
    }

    @Test
    @DisplayName("지아는 제 결과를 직접 적지 못한다 — 측정은 보호자 화면에서만")
    void 지아는_제_결과를_직접_적지_못한다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Family f = newFamily();
        String body = send(post("/api/v1/profiles/" + f.jiaId + "/fitness-tests"), f.jia, measureBody(), 403);
        assertThat((String) JsonPath.read(body, "$.error.code")).isEqualTo("NOT_A_PARENT");
    }

    @Test
    @DisplayName("지아가 운동을 끝내고 알리기 → 할머니가 스티커 → 지아가 고마워요, 알림이 차례로 온다")
    void 알리기_스티커_고마워요() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Family f = newFamily();
        measureJia(f);

        String missionId = planAndApprove(f, D1);
        completeAll(f.jia, missionId, f.jiaId);
        String mission = send(get("/api/v1/missions/" + missionId), f.jia, null, 200);
        List<Boolean> done = JsonPath.read(mission, "$.participants[?(@.profileId == '" + f.jiaId + "')].completed");
        assertThat(done).containsExactly(true);

        // 알리기 — 스티커 없이 아이 → 보호자면 서버가 DONE 으로 정한다
        String kidDone = send(
                post("/api/v1/families/" + f.familyId + "/cheers"),
                f.jia,
                "{\"fromProfileId\":\"" + f.jiaId + "\",\"toProfileId\":\"" + f.grandmaId
                        + "\",\"message\":\"다 했어요\",\"missionId\":\"" + missionId + "\"}",
                201);
        assertThat((String) JsonPath.read(kidDone, "$.kind")).isEqualTo("DONE");
        assertThat(kinds(f.grandma, f.grandmaId)).contains("KID_DONE");

        // 할머니가 스티커를 붙인다
        String praise = send(
                post("/api/v1/families/" + f.familyId + "/cheers"),
                f.grandma,
                "{\"fromProfileId\":\"" + f.grandmaId + "\",\"toProfileId\":\"" + f.jiaId
                        + "\",\"stickerId\":\"star\",\"missionId\":\"" + missionId + "\"}",
                201);
        assertThat((String) JsonPath.read(praise, "$.kind")).isEqualTo("PRAISE");
        String praiseId = JsonPath.read(praise, "$.cheerId");

        String jiaInbox = send(get("/api/v1/notifications?profileId=" + f.jiaId), f.jia, null, 200);
        List<String> praiseCheer = JsonPath.read(jiaInbox, "$.items[?(@.kind == 'PRAISE')].cheerId");
        assertThat(praiseCheer).containsExactly(praiseId);

        // 지아가 고마워요
        String thanksBody = "{\"fromProfileId\":\"" + f.jiaId + "\",\"toProfileId\":\"" + f.grandmaId
                + "\",\"kind\":\"THANKS\",\"stickerId\":\"heart\",\"replyToCheerId\":\"" + praiseId
                + "\",\"missionId\":\"" + missionId + "\"}";
        String thanks = send(post("/api/v1/families/" + f.familyId + "/cheers"), f.jia, thanksBody, 201);
        assertThat((String) JsonPath.read(thanks, "$.kind")).isEqualTo("THANKS");
        assertThat(kinds(f.grandma, f.grandmaId)).contains("KID_DONE", "KID_THANKS");

        // 같은 칭찬에 두 번 고마워요는 안 된다
        String again = send(post("/api/v1/families/" + f.familyId + "/cheers"), f.jia, thanksBody, 409);
        assertThat((String) JsonPath.read(again, "$.error.code")).isEqualTo("ALREADY_THANKED");

        // 스티커 경험치와 첫 스티커 업적
        String progress = send(get("/api/v1/profiles/" + f.jiaId + "/progress"), f.jia, null, 200);
        List<Object> sticker = JsonPath.read(progress, "$.achievements[?(@.code == 'FIRST_STICKER')].earnedAt");
        assertThat(sticker).doesNotContainNull().hasSize(1);
        List<String> xpKinds = JsonPath.read(progress, "$.recentXp[*].kind");
        assertThat(xpKinds).contains("STICKER");
    }

    // ---- 도우미 ----

    private record Family(String familyId, String grandma, String grandmaId, String jia, String jiaId) {}

    private Family newFamily() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String grandma = login("qa-D-grandma-" + suffix);
        String created = send(
                post("/api/v1/families"),
                grandma,
                "{\"familyName\":\"지아네\",\"owner\":{\"name\":\"할머니\",\"birthDate\":\"1963-05-01\",\"sex\":\"F\"}}",
                201);
        String familyId = JsonPath.read(created, "$.familyId");
        String grandmaId = JsonPath.read(created, "$.ownerProfile.profileId");
        // 만 15세 — guardianConsent 를 보내지 않는다
        String added = send(
                post("/api/v1/families/" + familyId + "/profiles"),
                grandma,
                "{\"name\":\"지아\",\"birthDate\":\"2011-03-01\",\"sex\":\"F\",\"role\":\"CHILD\"}",
                201);
        String jiaId = JsonPath.read(added, "$.profileId");
        assertThat((Boolean) JsonPath.read(added, "$.consentRequired")).isFalse();

        String invite = send(post("/api/v1/profiles/" + jiaId + "/invite"), grandma, null, 201);
        String code = JsonPath.read(invite, "$.claimCode");
        String jia = login("qa-D-jia-" + suffix);
        String claimed = send(post("/api/v1/profiles/claim"), jia, "{\"claimCode\":\"" + code + "\"}", 200);
        assertThat((String) JsonPath.read(claimed, "$.role")).isEqualTo("CHILD");
        assertThat((String) JsonPath.read(claimed, "$.nextStep")).isEqualTo("HOME");
        return new Family(familyId, grandma, grandmaId, jia, jiaId);
    }

    private static String measureBody() {
        return "{\"testedOn\":\"" + today() + "\",\"source\":\"SELF_INPUT\",\"heightCm\":160,\"weightKg\":50,"
                + "\"bodyFatPct\":24,\"waistCm\":66,\"items\":["
                + "{\"itemCode\":\"028\",\"value\":45},{\"itemCode\":\"009\",\"value\":30},"
                + "{\"itemCode\":\"012\",\"value\":10},{\"itemCode\":\"010\",\"value\":50}]}";
    }

    private void measureJia(Family f) throws Exception {
        send(post("/api/v1/profiles/" + f.jiaId + "/fitness-tests"), f.grandma, measureBody(), 201);
    }

    private String planAndApprove(Family f, LocalDate date) throws Exception {
        String started = send(
                post("/api/v1/families/" + f.familyId + "/coach/runs"),
                f.grandma,
                "{\"profileId\":\"" + f.jiaId + "\",\"date\":\"" + date
                        + "\",\"minutes\":20,\"quiet\":true,\"place\":\"HOME\",\"focusFactor\":null,\"withParent\":false}",
                202);
        String runId = JsonPath.read(started, "$.coachRunId");
        String run = send(get("/api/v1/coach/runs/" + runId), f.grandma, null, 200);
        assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("AWAITING_APPROVAL");
        String approved = send(post("/api/v1/coach/runs/" + runId + "/approve"), f.grandma, null, 200);
        List<String> missions = JsonPath.read(approved, "$.createdMissions[*].missionId");
        assertThat(missions).hasSize(1);
        return missions.get(0);
    }

    private void completeAll(String bearer, String missionId, String profileId) throws Exception {
        String mission = send(get("/api/v1/missions/" + missionId), bearer, null, 200);
        List<Integer> positions = JsonPath.read(mission, "$.sessions[*].position");
        List<Integer> minutes = JsonPath.read(mission, "$.sessions[*].minutes");
        assertThat(positions).isNotEmpty();
        for (int i = 0; i < positions.size(); i++) {
            int seconds = minutes.get(i) * 60;
            Instant end = CLOCK.instant();
            Instant start = end.minusSeconds(seconds);
            send(
                    post("/api/v1/missions/" + missionId + "/sessions/" + positions.get(i) + "/complete"),
                    bearer,
                    "{\"profileId\":\"" + profileId + "\",\"activeSeconds\":" + seconds + ",\"startedAt\":\"" + start
                            + "\",\"endedAt\":\"" + end + "\"}",
                    200);
        }
    }

    private List<String> kinds(String bearer, String profileId) throws Exception {
        String inbox = send(get("/api/v1/notifications?profileId=" + profileId), bearer, null, 200);
        return JsonPath.read(inbox, "$.items[*].kind");
    }

    /** dev-login 으로 계정을 만들고, 실제 시각 기준으로 살아 있는 액세스 토큰을 만든다 — 시험 시계가 과거여도 토큰이 만료되지 않게. */
    private String login(String providerUserId) throws Exception {
        String body =
                send(post("/api/v1/auth/dev-login"), null, "{\"providerUserId\":\"" + providerUserId + "\"}", 200);
        String userId = JsonPath.read(body, "$.userId");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.auth().jwt().issuer())
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(2)))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE_CLAIM, TokenClaims.ACCESS)
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return "Bearer " + token;
    }

    private String send(
            MockHttpServletRequestBuilder request, @Nullable String bearer, @Nullable String json, int expected)
            throws Exception {
        if (bearer != null) request.header(HttpHeaders.AUTHORIZATION, bearer);
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        MvcResult result = mvc.perform(request).andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus())
                .as(
                        "%s %s → %s",
                        result.getRequest().getMethod(), result.getRequest().getRequestURI(), body)
                .isEqualTo(expected);
        return body;
    }

    private static void at(LocalDate date, LocalTime time) {
        CLOCK.set(date.atTime(time).atZone(SEOUL).toInstant());
    }

    private static LocalDate today() {
        return LocalDate.ofInstant(CLOCK.instant(), SEOUL);
    }

    /** 시험에서 시각을 앞뒤로 돌릴 수 있는 시계. {@link #withZone} 으로 얻은 시계도 같은 시각을 따른다(identity 의 MutableClock 과 같은 방식). */
    static final class MovableClock extends Clock {
        private final Instant[] now;
        private final ZoneId zone;

        MovableClock(Instant start) {
            this(new Instant[] {start}, SEOUL);
        }

        private MovableClock(Instant[] now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void set(Instant instant) {
            now[0] = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MovableClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now[0];
        }
    }
}
