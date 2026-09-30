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
 * 여정 E — 할아버지(70)와 손주(6). 65세 이상은 성인 영상을 똑같이 받는다(공단 어르신 영상은 쓰지 않는다는 팀 결정).
 * 할아버지 측정 012 · 028 에는 AI 어르신 표로 백분위가 나오고, 등급 기준 줄은 없다(NO_CRITERIA).
 * 손주 편성에 할아버지가 같이(withParent) 붙는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-journey-elder;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.coach.poll-interval-ms=0",
            "app.coach.max-polls=3"
        })
class ElderJourneyTest {
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
    @DisplayName("할아버지 운동 찾기는 성인과 같은 목록이고, 공단 영상과 「공통」 영상이 들어 있다")
    void 할아버지_운동_찾기는_성인과_같은_목록이다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Elder e = newElderFamily();
        Adult a = newAdultFamily();

        String elderList = send(get("/api/v1/exercises"), e.grandpa, null, 200);
        String adultList = send(get("/api/v1/exercises"), a.adult, null, 200);
        String teenList = send(get("/api/v1/exercises?profileId=" + a.teenId), a.adult, null, 200);

        List<String> elderClips = JsonPath.read(elderList, "$.clips[*].clipId");
        List<String> adultClips = JsonPath.read(adultList, "$.clips[*].clipId");
        assertThat(elderClips).isNotEmpty().isEqualTo(adultClips);
        assertThat((Integer) JsonPath.read(elderList, "$.total"))
                .isEqualTo((Integer) JsonPath.read(adultList, "$.total"));

        // 공단 영상(mp4)이 섞여 있다
        List<String> elderKspo = JsonPath.read(elderList, "$.clips[?(@.mediaUrl != null)].clipId");
        assertThat(elderKspo).isNotEmpty();

        // 「공통」(청소년 · 성인) 영상 — 요인으로 좁혀 청소년 목록과 겹치는 공단 영상이 있는지 본다
        boolean sharedFound = false;
        for (String factor : List.of("근력", "근지구력", "유연성", "심폐지구력", "순발력", "민첩성")) {
            // 주소에 인코딩한 값을 붙이면 MockMvc 가 한 번 더 인코딩하므로 param 으로 넘긴다
            List<String> elderByFactor = JsonPath.read(
                    send(get("/api/v1/exercises").param("factor", factor), e.grandpa, null, 200),
                    "$.clips[?(@.mediaUrl != null)].clipId");
            List<String> teenByFactor = JsonPath.read(
                    send(
                            get("/api/v1/exercises").param("factor", factor).param("profileId", a.teenId),
                            a.adult,
                            null,
                            200),
                    "$.clips[?(@.mediaUrl != null)].clipId");
            if (elderByFactor.stream().anyMatch(teenByFactor::contains)) {
                sharedFound = true;
                break;
            }
        }
        assertThat(sharedFound).as("어르신 목록에 청소년과 함께 받는 「공통」 공단 영상이 있어야 한다").isTrue();
        assertThat(teenList).isNotEqualTo(elderList);
    }

    @Test
    @DisplayName("할아버지 측정 012 · 028 에 백분위가 나오고 등급 기준 줄은 없다")
    void 할아버지_측정_012_028_에_백분위가_나온다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Elder e = newElderFamily();

        String body = send(
                post("/api/v1/profiles/" + e.grandpaId + "/fitness-tests"),
                e.grandpa,
                "{\"testedOn\":\"" + today() + "\",\"source\":\"SELF_INPUT\",\"heightCm\":168,\"weightKg\":66,"
                        + "\"items\":[{\"itemCode\":\"012\",\"value\":5.0},{\"itemCode\":\"028\",\"value\":55.0}]}",
                201);
        List<Object> p012 = JsonPath.read(body, "$.items[?(@.itemCode == '012')].percentile");
        List<Object> p028 = JsonPath.read(body, "$.items[?(@.itemCode == '028')].percentile");
        assertThat(p012).hasSize(1).doesNotContainNull();
        assertThat(p028).hasSize(1).doesNotContainNull();
        assertThat((String) JsonPath.read(body, "$.certification.status")).isEqualTo("NO_CRITERIA");
        assertThat((Object) JsonPath.read(body, "$.certification.grade")).isNull();

        String latest = send(get("/api/v1/profiles/" + e.grandpaId + "/fitness-tests/latest"), e.grandpa, null, 200);
        List<Object> latestPercentiles = JsonPath.read(latest, "$.items[*].percentile");
        assertThat(latestPercentiles).hasSize(2).doesNotContainNull();
    }

    @Test
    @DisplayName("할아버지 본인 편성에는 성인 영상이 붙는다")
    void 할아버지_본인_편성에는_성인_영상이_붙는다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Elder e = newElderFamily();
        send(
                post("/api/v1/profiles/" + e.grandpaId + "/fitness-tests"),
                e.grandpa,
                "{\"testedOn\":\"" + today() + "\",\"source\":\"SELF_INPUT\","
                        + "\"items\":[{\"itemCode\":\"012\",\"value\":5.0},{\"itemCode\":\"028\",\"value\":55.0}]}",
                201);

        String run = plan(e, e.grandpaId, false);
        assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("AWAITING_APPROVAL");

        // 스텁은 19세가 넘은 사람에게 성인 영상(IhShIA-WJNE)을 붙인다 — 어르신도 같은 영상
        assertThat(JsonPath.<List<String>>read(run, "$.proposals[*].video.videoId"))
                .containsOnly("IhShIA-WJNE");
    }

    @Test
    @DisplayName("손주 편성에 할아버지가 같이 하면 할아버지가 동반자로 붙고, 손주 칸을 끝내면 할아버지 몫도 적힌다")
    void 손주_편성에_할아버지가_같이_한다() throws Exception {
        at(D1, LocalTime.of(19, 0));
        Elder e = newElderFamily();
        send(
                post("/api/v1/profiles/" + e.childId + "/fitness-tests"),
                e.grandpa,
                "{\"testedOn\":\"" + today() + "\",\"source\":\"SELF_INPUT\",\"heightCm\":118,\"weightKg\":22,"
                        + "\"items\":[{\"itemCode\":\"012\",\"value\":8.0},{\"itemCode\":\"022\",\"value\":100},"
                        + "{\"itemCode\":\"009\",\"value\":10}]}",
                201);

        String run = plan(e, e.childId, true);
        String runId = JsonPath.read(run, "$.coachRunId");
        List<String> companions =
                JsonPath.read(run, "$.proposals[0].participants[?(@.profileId == '" + e.grandpaId + "')].coachRole");
        assertThat(companions).containsExactly("동반자");

        String approved = send(post("/api/v1/coach/runs/" + runId + "/approve"), e.grandpa, null, 200);
        String missionId = JsonPath.<List<String>>read(approved, "$.createdMissions[*].missionId")
                .get(0);
        completeAll(e.grandpa, missionId, e.childId);

        String mission = send(get("/api/v1/missions/" + missionId), e.grandpa, null, 200);
        List<Boolean> childDone =
                JsonPath.read(mission, "$.participants[?(@.profileId == '" + e.childId + "')].completed");
        List<Boolean> grandpaDone =
                JsonPath.read(mission, "$.participants[?(@.profileId == '" + e.grandpaId + "')].completed");
        assertThat(childDone).containsExactly(true);
        assertThat(grandpaDone).containsExactly(true);
    }

    // ---- 도우미 ----

    private record Elder(String familyId, String grandpa, String grandpaId, String childId) {}

    private record Adult(String adult, String teenId) {}

    private Elder newElderFamily() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String grandpa = login("qa-E-grandpa-" + suffix);
        String created = send(
                post("/api/v1/families"),
                grandpa,
                "{\"familyName\":\"할아버지네\",\"owner\":{\"name\":\"할아버지\",\"birthDate\":\"1956-01-15\",\"sex\":\"M\"}}",
                201);
        String familyId = JsonPath.read(created, "$.familyId");
        String grandpaId = JsonPath.read(created, "$.ownerProfile.profileId");
        assertThat((String) JsonPath.read(created, "$.ownerProfile.ageGroup")).isEqualTo("어르신");
        String child = send(
                post("/api/v1/families/" + familyId + "/profiles"),
                grandpa,
                "{\"name\":\"손주\",\"birthDate\":\"2020-05-01\",\"sex\":\"M\",\"role\":\"CHILD\","
                        + "\"guardianConsent\":{\"personalData\":true,\"healthData\":true}}",
                201);
        return new Elder(familyId, grandpa, grandpaId, JsonPath.read(child, "$.profileId"));
    }

    private Adult newAdultFamily() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adult = login("qa-E-adult-" + suffix);
        String created = send(
                post("/api/v1/families"),
                adult,
                "{\"familyName\":\"비교네\",\"owner\":{\"name\":\"어른\",\"birthDate\":\"1986-04-01\",\"sex\":\"M\"}}",
                201);
        String familyId = JsonPath.read(created, "$.familyId");
        String teen = send(
                post("/api/v1/families/" + familyId + "/profiles"),
                adult,
                "{\"name\":\"청소년\",\"birthDate\":\"2011-03-01\",\"sex\":\"M\",\"role\":\"CHILD\"}",
                201);
        return new Adult(adult, JsonPath.read(teen, "$.profileId"));
    }

    private String plan(Elder e, String profileId, boolean withParent) throws Exception {
        String started = send(
                post("/api/v1/families/" + e.familyId + "/coach/runs"),
                e.grandpa,
                "{\"profileId\":\"" + profileId + "\",\"date\":\"" + today()
                        + "\",\"minutes\":20,\"quiet\":true,\"place\":\"HOME\",\"focusFactor\":null,\"withParent\":"
                        + withParent + "}",
                202);
        String runId = JsonPath.read(started, "$.coachRunId");
        return send(get("/api/v1/coach/runs/" + runId), e.grandpa, null, 200);
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
