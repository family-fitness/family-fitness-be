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
 * 여정 리그 — 9월 마지막 날(D1)에 세 가족이 리그를 열고, 10/1 00:10 월초 정산을 직접 부른다.
 * 방에 든 가족이 8 미만이라 오르내림 자리가 0 이고, 1등 가족도 같은 티어(브론즈)로 10월 방에 들어간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-journey-league;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.coach.poll-interval-ms=0",
            "app.coach.max-polls=3"
        })
class LeagueJourneyTest {
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
    kr.ac.kookmin.familyfitness.league.application.LeagueSettlementScheduler settlementScheduler;

    @Test
    @DisplayName("8가족 미만 방은 월초 정산 뒤에도 오르내림 없이 같은 티어로 다음 달 방에 들어간다")
    void 여덟_가족_미만이면_오르내림이_없다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Home done = newFamily("해낸네");
        Home skipped = newFamily("미룬네");
        Home idle = newFamily("쉰네");

        String doneMission = planAndApprove(done);
        planAndApprove(skipped);
        completeAll(done.parent, doneMission, done.childId);

        // 가족은 리그를 처음 열 때 방에 들어간다 — 세 가족이 모두 한 번씩 연 뒤에 방 크기를 본다
        for (Home h : List.of(done, skipped, idle)) {
            send(get("/api/v1/families/" + h.familyId + "/league"), h.parent, null, 200);
        }
        for (Home h : List.of(done, skipped, idle)) {
            String sep = send(get("/api/v1/families/" + h.familyId + "/league"), h.parent, null, 200);
            assertThat((String) JsonPath.read(sep, "$.month")).isEqualTo("2026-09");
            assertThat((String) JsonPath.read(sep, "$.tier")).isEqualTo("BRONZE");
            assertThat((Integer) JsonPath.read(sep, "$.groupSize")).isEqualTo(3);
            assertThat((Integer) JsonPath.read(sep, "$.promote")).isZero();
            assertThat((Integer) JsonPath.read(sep, "$.demote")).isZero();
            assertThat((Integer) JsonPath.read(sep, "$.daysLeft")).isZero();
        }
        String doneSep = send(get("/api/v1/families/" + done.familyId + "/league"), done.parent, null, 200);
        assertThat(((Number) JsonPath.read(doneSep, "$.rate")).intValue()).isEqualTo(100);
        assertThat((Integer) JsonPath.read(doneSep, "$.rank")).isEqualTo(1);

        // D2 00:10 — 월초 정산
        at(D1.plusDays(1), LocalTime.of(0, 10));
        settlementScheduler.run();

        String doneClosed =
                send(get("/api/v1/families/" + done.familyId + "/league?month=2026-09"), done.parent, null, 200);
        assertThat(((Number) JsonPath.read(doneClosed, "$.rate")).intValue()).isEqualTo(100);
        assertThat((Integer) JsonPath.read(doneClosed, "$.rank")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(doneClosed, "$.promote")).isZero();

        // 잡힌 날(D1)을 빼먹은 가족은 0%, 잡힌 날이 없는 가족은 null 로 맨 아래
        String skippedClosed =
                send(get("/api/v1/families/" + skipped.familyId + "/league?month=2026-09"), skipped.parent, null, 200);
        assertThat(((Number) JsonPath.read(skippedClosed, "$.rate")).intValue()).isZero();
        assertThat((Integer) JsonPath.read(skippedClosed, "$.rank")).isEqualTo(2);
        String idleClosed =
                send(get("/api/v1/families/" + idle.familyId + "/league?month=2026-09"), idle.parent, null, 200);
        assertThat((Object) JsonPath.read(idleClosed, "$.rate")).isNull();
        assertThat((Object) JsonPath.read(idleClosed, "$.rank")).isNull();

        for (Home h : List.of(done, skipped, idle)) {
            String oct = send(get("/api/v1/families/" + h.familyId + "/league"), h.parent, null, 200);
            assertThat((String) JsonPath.read(oct, "$.month")).isEqualTo("2026-10");
            assertThat((String) JsonPath.read(oct, "$.tier")).as(h.name).isEqualTo("BRONZE");
            assertThat((Integer) JsonPath.read(oct, "$.groupSize")).isEqualTo(3);
            assertThat((Integer) JsonPath.read(oct, "$.promote")).isZero();
            assertThat((Integer) JsonPath.read(oct, "$.demote")).isZero();
            assertThat((Integer) JsonPath.read(oct, "$.daysLeft")).isEqualTo(30);
        }

        // 정산을 한 번 더 돌려도 결과가 같다
        settlementScheduler.run();
        String again = send(get("/api/v1/families/" + done.familyId + "/league"), done.parent, null, 200);
        assertThat((String) JsonPath.read(again, "$.tier")).isEqualTo("BRONZE");
        assertThat((Integer) JsonPath.read(again, "$.groupSize")).isEqualTo(3);
    }

    // ---- 도우미 ----

    private record Home(String name, String familyId, String parent, String childId) {}

    private Home newFamily(String name) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String parent = login("qa-league-" + suffix);
        String created = send(
                post("/api/v1/families"),
                parent,
                "{\"familyName\":\"" + name
                        + "\",\"owner\":{\"name\":\"보호자\",\"birthDate\":\"1985-06-01\",\"sex\":\"F\"}}",
                201);
        String familyId = JsonPath.read(created, "$.familyId");
        String child = send(
                post("/api/v1/families/" + familyId + "/profiles"),
                parent,
                "{\"name\":\"아이\",\"birthDate\":\"2015-02-01\",\"sex\":\"F\",\"role\":\"CHILD\","
                        + "\"guardianConsent\":{\"personalData\":true,\"healthData\":true}}",
                201);
        String childId = JsonPath.read(child, "$.profileId");
        send(
                post("/api/v1/profiles/" + childId + "/fitness-tests"),
                parent,
                "{\"testedOn\":\"" + today() + "\",\"source\":\"SELF_INPUT\","
                        + "\"items\":[{\"itemCode\":\"012\",\"value\":8},{\"itemCode\":\"009\",\"value\":20}]}",
                201);
        return new Home(name, familyId, parent, childId);
    }

    private String planAndApprove(Home h) throws Exception {
        String started = send(
                post("/api/v1/families/" + h.familyId + "/coach/runs"),
                h.parent,
                "{\"profileId\":\"" + h.childId + "\",\"date\":\"" + today()
                        + "\",\"minutes\":20,\"quiet\":true,\"place\":\"HOME\",\"focusFactor\":null,\"withParent\":false}",
                202);
        String runId = JsonPath.read(started, "$.coachRunId");
        String approved = send(post("/api/v1/coach/runs/" + runId + "/approve"), h.parent, null, 200);
        return JsonPath.<List<String>>read(approved, "$.createdMissions[*].missionId")
                .get(0);
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
