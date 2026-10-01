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
 * 여정 G. 이탈형이다. 엄마(36)와 딸(9). 측정 전에도 키와 몸무게로 편성을 받고,
 * D3 에 재고 D3~D5 편성을 매번 거절한 뒤 D6~D10 다섯 날을 쉬고 D11 에 돌아와 운동한다.
 * 날을 넘길 때 07:30 MISSION_READY 작업을 직접 부른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-journey-dropout;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.coach.poll-interval-ms=0",
            "app.coach.max-polls=3"
        })
class DropoutJourneyTest {
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

    @Autowired
    kr.ac.kookmin.familyfitness.notification.application.NotificationScheduler notificationScheduler;

    @Test
    @DisplayName("측정을 하지 않은 아이도 키와 몸무게를 실어 편성을 받고(202), 그 편성은 승인을 기다리는 제안이 된다")
    void 측정_전에도_키와_몸무게로_편성을_받는다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Mom m = newFamily();
        String started = send(
                post("/api/v1/families/" + m.familyId + "/coach/runs"),
                m.mom,
                "{\"profileId\":\"" + m.daughterId + "\",\"date\":\"" + today()
                        + "\",\"minutes\":20,\"quiet\":true,\"place\":\"HOME\",\"focusFactor\":null,\"withParent\":false,"
                        + "\"heightCm\":132,\"weightKg\":28}",
                202);
        assertThat((String) JsonPath.read(started, "$.status")).isEqualTo("RUNNING");

        String run = send(get("/api/v1/coach/runs/" + JsonPath.read(started, "$.coachRunId")), m.mom, null, 200);
        assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("AWAITING_APPROVAL");
        assertThat(JsonPath.<List<Object>>read(run, "$.proposals")).hasSize(1);
    }

    @Test
    @DisplayName("9살 측정은 또래 비교가 없다 — 백분위는 모두 비고 등급 기준 줄도 없다(NO_CRITERIA)")
    void 아홉_살_측정은_또래_비교가_없다() throws Exception {
        at(D1.plusDays(2), LocalTime.of(19, 0));
        Mom m = newFamily();
        String body = measure(m);
        List<Object> percentiles = JsonPath.read(body, "$.items[*].percentile");
        assertThat(percentiles).hasSize(5).containsOnlyNulls();
        assertThat((String) JsonPath.read(body, "$.certification.status")).isEqualTo("NO_CRITERIA");
        assertThat((Object) JsonPath.read(body, "$.certification.grade")).isNull();

        // 잰 아이의 편성도 그대로 된다
        String started = startPlan(m, 202);
        assertThat((String) JsonPath.read(started, "$.status")).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("사흘 내리 편성을 거절하면 미션이 생기지 않고, 다음 날 07:30 MISSION_READY 도 없다")
    void 사흘_내리_거절하면_미션도_알림도_없다() throws Exception {
        at(D1.plusDays(2), LocalTime.of(19, 0));
        Mom m = newFamily();
        measure(m);
        for (int day = 2; day <= 4; day++) {
            at(D1.plusDays(day), LocalTime.of(19, 0));
            String runId = JsonPath.read(startPlan(m, 202), "$.coachRunId");
            String rejected =
                    send(post("/api/v1/coach/runs/" + runId + "/reject"), m.mom, "{\"reason\":\"오늘은 쉴래요\"}", 200);
            assertThat((String) JsonPath.read(rejected, "$.status")).isEqualTo("REJECTED");
            assertThat((Integer) JsonPath.read(rejected, "$.missionCount")).isZero();
            String latest = send(
                    get("/api/v1/families/" + m.familyId + "/coach/runs/latest?profileId=" + m.daughterId),
                    m.mom,
                    null,
                    200);
            assertThat((String) JsonPath.read(latest, "$.status")).isEqualTo("REJECTED");
        }
        at(D1.plusDays(5), LocalTime.of(7, 30));
        notificationScheduler.missionReady();

        String missions = send(get("/api/v1/families/" + m.familyId + "/missions"), m.mom, null, 200);
        assertThat(JsonPath.<List<Object>>read(missions, "$.missions")).isEmpty();
        assertThat(kinds(m.mom, m.daughterId)).doesNotContain("MISSION_READY");
    }

    @Test
    @DisplayName("닷새 쉬고 D11 에 돌아와 운동하면 이어서 한 날 1일 · 첫걸음 · 리그 100%, 끝낸 운동의 MISSION_READY 는 빠진다")
    void 닷새_쉬고_돌아와_운동한다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Mom m = newFamily();
        // 재기 전에도 편성은 받는다. 이 제안은 승인하지 않고 둔다
        startPlan(m, 202);

        at(D1.plusDays(2), LocalTime.of(19, 0));
        measure(m);
        for (int day = 2; day <= 4; day++) {
            at(D1.plusDays(day), LocalTime.of(19, 0));
            String runId = JsonPath.read(startPlan(m, 202), "$.coachRunId");
            send(post("/api/v1/coach/runs/" + runId + "/reject"), m.mom, null, 200);
        }
        // D6 ~ D11 아침: 아무것도 하지 않는 날에도 07:30 작업은 돈다
        for (int day = 5; day <= 10; day++) {
            at(D1.plusDays(day), LocalTime.of(7, 30));
            notificationScheduler.missionReady();
        }
        assertThat(kinds(m.mom, m.daughterId)).doesNotContain("MISSION_READY");

        // D11 저녁에 돌아온다
        LocalDate d11 = D1.plusDays(10);
        at(d11, LocalTime.of(19, 0));
        String runId = JsonPath.read(startPlan(m, 202), "$.coachRunId");
        String run = send(get("/api/v1/coach/runs/" + runId), m.mom, null, 200);
        assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("AWAITING_APPROVAL");
        String approved = send(post("/api/v1/coach/runs/" + runId + "/approve"), m.mom, null, 200);
        String missionId = JsonPath.<List<String>>read(approved, "$.createdMissions[*].missionId")
                .get(0);
        // 07:30 뒤에 생긴 운동은 생길 때 MISSION_READY 가 온다
        assertThat(kinds(m.mom, m.daughterId)).contains("MISSION_READY");

        completeAll(m.mom, missionId, m.daughterId);

        String progress = send(get("/api/v1/profiles/" + m.daughterId + "/progress"), m.mom, null, 200);
        assertThat((Integer) JsonPath.read(progress, "$.streakDays")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(progress, "$.activeDays")).isEqualTo(1);
        List<Object> firstStep = JsonPath.read(progress, "$.achievements[?(@.code == 'FIRST_STEP')].earnedAt");
        assertThat(firstStep).hasSize(1).doesNotContainNull();

        List<String> inbox = kinds(m.mom, m.daughterId);
        assertThat(inbox).contains("ACHIEVEMENT").doesNotContain("MISSION_READY");

        String league = send(get("/api/v1/families/" + m.familyId + "/league"), m.mom, null, 200);
        assertThat((String) JsonPath.read(league, "$.month")).isEqualTo("2026-10");
        assertThat(((Number) JsonPath.read(league, "$.rate")).intValue()).isEqualTo(100);
    }

    // ---- 도우미 ----

    private record Mom(String familyId, String mom, String momId, String daughterId) {}

    private Mom newFamily() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String mom = login("qa-G-mom-" + suffix);
        String created = send(
                post("/api/v1/families"),
                mom,
                "{\"familyName\":\"이탈네\",\"owner\":{\"name\":\"엄마\",\"birthDate\":\"1990-03-01\",\"sex\":\"F\"}}",
                201);
        String familyId = JsonPath.read(created, "$.familyId");
        String momId = JsonPath.read(created, "$.ownerProfile.profileId");
        String daughter = send(
                post("/api/v1/families/" + familyId + "/profiles"),
                mom,
                "{\"name\":\"딸\",\"birthDate\":\"2017-04-01\",\"sex\":\"F\",\"role\":\"CHILD\","
                        + "\"guardianConsent\":{\"personalData\":true,\"healthData\":true}}",
                201);
        return new Mom(familyId, mom, momId, JsonPath.read(daughter, "$.profileId"));
    }

    private String measure(Mom m) throws Exception {
        return send(
                post("/api/v1/profiles/" + m.daughterId + "/fitness-tests"),
                m.mom,
                "{\"testedOn\":\"" + today() + "\",\"source\":\"SELF_INPUT\",\"heightCm\":132,\"weightKg\":28,"
                        + "\"items\":[{\"itemCode\":\"028\",\"value\":30},{\"itemCode\":\"009\",\"value\":15},"
                        + "{\"itemCode\":\"012\",\"value\":5},{\"itemCode\":\"043\",\"value\":20},"
                        + "{\"itemCode\":\"022\",\"value\":120}]}",
                201);
    }

    private String startPlan(Mom m, int expected) throws Exception {
        return send(
                post("/api/v1/families/" + m.familyId + "/coach/runs"),
                m.mom,
                "{\"profileId\":\"" + m.daughterId + "\",\"date\":\"" + today()
                        + "\",\"minutes\":20,\"quiet\":true,\"place\":\"HOME\",\"focusFactor\":null,\"withParent\":false}",
                expected);
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
