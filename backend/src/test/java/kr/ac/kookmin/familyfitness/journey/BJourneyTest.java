package kr.ac.kookmin.familyfitness.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunExecutorConfig;
import kr.ac.kookmin.familyfitness.notification.application.NotificationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 여정 B 하윤이네 — 엄마 계정 하나에 아이 셋(도윤 13 · 하윤 12 · 서아 5, 모두 계정 없음).
 * 도윤은 학교 결과를 측정일 9/1 로 옮겨 적는다 → D2(10/1) 09:00 에 다시 재기 알림(REMEASURE) → D3 에 다시 재면 알림이 지워지고 더 오지 않는다.
 * 하윤은 직접 짜기로 D2~D14 를 한 번에 넣고 날마다 끝낸다. 도윤 · 서아는 날마다 번갈아 AI 편성을 받는다.
 * 시각을 마음대로 바꿀 수 있는 Clock 으로 바꾸고, 날을 넘길 때 예약 작업(07:30 · 09:00 알림)을 직접 부른다. AI 는 스텁이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-journey-b;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.auth.dev-auto-login.enabled=true",
            "app.coach.poll-interval-ms=0",
            "app.coach.max-polls=3"
        })
class BJourneyTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate D1 = LocalDate.of(2026, 9, 30);
    private static final MovableClock CLOCK = new MovableClock(at(D1, 6, 0));

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
    NotificationScheduler notifications;

    @Test
    @DisplayName("하윤이네 2주: 9/1 에 잰 도윤에게 10/1 09:00 다시 재기 알림이 오고, 다시 재면 사라져 더 오지 않는다")
    void 도윤_다시_재기_알림() throws Exception {
        CLOCK.set(at(D1, 6, 0));
        String mom = devLogin("qa-B-mom");
        String familyId = createFamily(mom, "하윤이네", "엄마", "1988-02-01", "F");
        String momProfile = selfProfile(mom);
        String doyun = addChild(mom, familyId, "도윤", "2013-05-01", "M");

        // 학교 결과를 측정일 9/1 로 옮겨 적는다
        created(as(mom, post("/api/v1/profiles/" + doyun + "/fitness-tests"))
                .content(fitnessBody(
                        LocalDate.of(2026, 9, 1), "158", "48", "68", Map.of("012", "10.0", "028", "50.0"))));

        // D1 09:00 — 29일밖에 안 지나 알림이 없다
        CLOCK.set(at(D1, 9, 0));
        notifications.remeasure();
        CLOCK.set(at(D1, 9, 1));
        assertThat(remeasureAbout(mom, momProfile, doyun))
                .as("D1 09:01 다시 재기 알림")
                .isZero();

        // D2(10/1) 09:00 — 30일이 지나 엄마 알림함에 한 번 온다
        LocalDate d2 = D1.plusDays(1);
        CLOCK.set(at(d2, 9, 0));
        notifications.remeasure();
        notifications.remeasure(); // 두 번 돌아도 한 번만
        CLOCK.set(at(d2, 9, 1));
        assertThat(remeasureAbout(mom, momProfile, doyun))
                .as("D2 09:01 다시 재기 알림")
                .isEqualTo(1);

        // D3 에 다시 잰다 → 알림이 지워진다
        LocalDate d3 = D1.plusDays(2);
        CLOCK.set(at(d3, 9, 0));
        notifications.remeasure();
        CLOCK.set(at(d3, 19, 0));
        created(as(mom, post("/api/v1/profiles/" + doyun + "/fitness-tests"))
                .content(fitnessBody(d3, "158", "48", "68", Map.of("012", "11.0", "028", "52.0"))));
        assertThat(remeasureAbout(mom, momProfile, doyun)).as("D3 다시 잰 뒤").isZero();

        // D4 09:00 — 더 오지 않는다
        LocalDate d4 = D1.plusDays(3);
        CLOCK.set(at(d4, 9, 0));
        notifications.remeasure();
        CLOCK.set(at(d4, 9, 1));
        assertThat(remeasureAbout(mom, momProfile, doyun))
                .as("D4 09:01 다시 재기 알림")
                .isZero();
        // 다시 잰 업적
        assertEarned(progress(mom, doyun), "REMEASURE", true);
    }

    @Test
    @DisplayName("하윤이네 2주: 하윤은 직접 짜기로 D2~D14 를 한 번에 넣어 날마다 끝내고, 도윤 · 서아는 번갈아 편성을 받아 끝낸다")
    void 하윤_직접_짜기_두_주_도윤_서아_번갈아() throws Exception {
        CLOCK.set(at(D1, 6, 0));
        String mom = devLogin("qa-B2-mom");
        String familyId = createFamily(mom, "하윤이네둘", "엄마", "1988-02-01", "F");
        String doyun = addChild(mom, familyId, "도윤", "2013-05-01", "M");
        String hayun = addChild(mom, familyId, "하윤", "2014-06-01", "F");
        String seoa = addChild(mom, familyId, "서아", "2021-03-01", "F");
        created(as(mom, post("/api/v1/profiles/" + doyun + "/fitness-tests"))
                .content(fitnessBody(D1, "158", "48", "68", Map.of("012", "10.0", "028", "50.0"))));
        created(as(mom, post("/api/v1/profiles/" + seoa + "/fitness-tests"))
                .content(fitnessBody(D1, "108", "18", null, Map.of("012", "10.0", "009", "20"))));

        // 하윤: D2~D14 열세 날을 한 번에
        StringBuilder dates = new StringBuilder();
        for (int day = 2; day <= 14; day++) {
            if (dates.length() > 0) dates.append(',');
            dates.append('"').append(D1.plusDays(day - 1)).append('"');
        }
        String manual = body(created(as(mom, post("/api/v1/families/" + familyId + "/missions"))
                .content("{\"title\":\"하윤 줄넘기\",\"dates\":[" + dates + "],\"targetMetric\":\"TIMER_MINUTES\","
                        + "\"targetValue\":10,\"participantProfileIds\":[\"" + hayun + "\"]}")));
        List<String> hayunMissions = read(manual, "$.missions[*].missionId");
        List<String> hayunStarts = read(manual, "$.missions[*].startDate");
        assertThat(hayunMissions).hasSize(13);
        assertThat(hayunStarts.getFirst()).isEqualTo(D1.plusDays(1).toString());

        for (int day = 1; day <= 14; day++) {
            LocalDate date = D1.plusDays(day - 1);
            String kid = day % 2 == 1 ? doyun : seoa;
            CLOCK.set(at(date, 19, 0));
            completeAll(mom, planAndApprove(mom, familyId, kid, date, false), kid);

            if (day >= 2) {
                String todays = hayunMissions.get(day - 2);
                if (day == 2) {
                    // 앞날 운동은 끝낼 수 없다
                    expect(completeRequest(mom, hayunMissions.get(1), 1, hayun, 600), 422);
                    // 형제 이름으로 하윤의 운동을 끝낼 수 없다
                    expect(completeRequest(mom, todays, 1, doyun, 600), 403);
                }
                completeAll(mom, todays, hayun);
                assertThat((Integer) read(progress(mom, hayun), "$.streakDays"))
                        .as("D%d 하윤 이어서 한 날", day)
                        .isEqualTo(day - 1);
            }
        }
        // 도윤은 홀수 날, 서아는 짝수 날만 잡혔다 — 잡힌 날을 다 했으니 이어진다
        assertThat((Integer) read(progress(mom, doyun), "$.streakDays")).isEqualTo(7);
        assertThat((Integer) read(progress(mom, seoa), "$.streakDays")).isEqualTo(7);
        assertEarned(progress(mom, hayun), "STREAK_7", true);
    }

    // ---- 도우미 ----

    private long remeasureAbout(String userId, String inboxProfileId, String aboutProfileId) throws Exception {
        List<Object> found = read(
                body(ok(as(userId, get("/api/v1/notifications?profileId=" + inboxProfileId)))),
                "$.items[?(@.kind == 'REMEASURE' && @.aboutProfileId == '" + aboutProfileId + "')]");
        return found.size();
    }

    private MockHttpServletRequestBuilder completeRequest(
            String userId, String missionId, int position, String profileId, int seconds) {
        Instant end = CLOCK.instant();
        return as(userId, post("/api/v1/missions/" + missionId + "/sessions/" + position + "/complete"))
                .content("{\"profileId\":\"" + profileId + "\",\"activeSeconds\":" + seconds + ",\"startedAt\":\""
                        + end.minusSeconds(seconds) + "\",\"endedAt\":\"" + end + "\"}");
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(LocalTime.of(hour, minute)).atZone(SEOUL).toInstant();
    }

    private String devLogin(String providerUserId) throws Exception {
        String res = body(mvc.perform(post("/api/v1/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerUserId\":\"" + providerUserId + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
        return read(res, "$.userId");
    }

    private String selfProfile(String userId) throws Exception {
        return read(body(ok(as(userId, get("/api/v1/me")))), "$.selfProfileId");
    }

    private String createFamily(String userId, String name, String owner, String birth, String sex) throws Exception {
        String res = body(created(as(userId, post("/api/v1/families"))
                .content("{\"familyName\":\"" + name + "\",\"owner\":{\"name\":\"" + owner + "\",\"birthDate\":\""
                        + birth + "\",\"sex\":\"" + sex + "\"}}")));
        return read(res, "$.familyId");
    }

    private String addChild(String userId, String familyId, String name, String birth, String sex) throws Exception {
        return read(
                body(
                        created(
                                as(userId, post("/api/v1/families/" + familyId + "/profiles"))
                                        .content(
                                                "{\"name\":\"" + name + "\",\"birthDate\":\"" + birth + "\",\"sex\":\""
                                                        + sex
                                                        + "\",\"role\":\"CHILD\",\"guardianConsent\":{\"personalData\":true,\"healthData\":true}}"))),
                "$.profileId");
    }

    private String addParent(String userId, String familyId, String name, String birth, String sex) throws Exception {
        return read(
                body(created(as(userId, post("/api/v1/families/" + familyId + "/profiles"))
                        .content("{\"name\":\"" + name + "\",\"birthDate\":\"" + birth + "\",\"sex\":\"" + sex
                                + "\",\"role\":\"PARENT\"}"))),
                "$.profileId");
    }

    private static String fitnessBody(
            LocalDate on, String height, String weight, String waist, Map<String, String> items) {
        StringBuilder sb = new StringBuilder("{\"testedOn\":\"" + on + "\",\"source\":\"SELF_INPUT\"");
        if (height != null) sb.append(",\"heightCm\":").append(height);
        if (weight != null) sb.append(",\"weightKg\":").append(weight);
        if (waist != null) sb.append(",\"waistCm\":").append(waist);
        sb.append(",\"items\":[");
        boolean first = true;
        for (Map.Entry<String, String> it : items.entrySet()) {
            if (!first) sb.append(',');
            sb.append("{\"itemCode\":\"")
                    .append(it.getKey())
                    .append("\",\"value\":")
                    .append(it.getValue())
                    .append('}');
            first = false;
        }
        return sb.append("]}").toString();
    }

    /** 아이 한 명의 하루를 편성하고 승인한다. 만든 미션 id 를 준다. */
    private String planAndApprove(String userId, String familyId, String profileId, LocalDate date, boolean withParent)
            throws Exception {
        String accepted = body(mvc.perform(as(userId, post("/api/v1/families/" + familyId + "/coach/runs"))
                        .content("{\"profileId\":\"" + profileId + "\",\"date\":\"" + date
                                + "\",\"minutes\":20,\"quiet\":true,\"place\":\"HOME\",\"focusFactor\":null,\"withParent\":"
                                + withParent + "}"))
                .andExpect(status().isAccepted())
                .andReturn());
        String runId = read(accepted, "$.coachRunId");
        String run = body(ok(as(userId, get("/api/v1/coach/runs/" + runId))));
        assertThat((String) read(run, "$.status")).as("편성 상태").isEqualTo("AWAITING_APPROVAL");
        String approved = body(ok(as(userId, post("/api/v1/coach/runs/" + runId + "/approve"))));
        return read(approved, "$.createdMissions[0].missionId");
    }

    /** 미션의 칸을 차례로 모두 끝낸다(보호자가 대신). */
    private void completeAll(String userId, String missionId, String profileId) throws Exception {
        String mission = body(ok(as(userId, get("/api/v1/missions/" + missionId))));
        List<Integer> positions = read(mission, "$.sessions[*].position");
        List<Integer> minutes = read(mission, "$.sessions[*].minutes");
        if (positions.isEmpty()) {
            positions = List.of(1);
            minutes = List.of(((Number) read(mission, "$.targetValue")).intValue());
        }
        for (int i = 0; i < positions.size(); i++) {
            int seconds = minutes.get(i) * 60;
            Instant end = CLOCK.instant();
            ok(as(userId, post("/api/v1/missions/" + missionId + "/sessions/" + positions.get(i) + "/complete"))
                    .content("{\"profileId\":\"" + profileId + "\",\"activeSeconds\":" + seconds + ",\"startedAt\":\""
                            + end.minusSeconds(seconds) + "\",\"endedAt\":\"" + end + "\"}"));
        }
    }

    private String progress(String userId, String profileId) throws Exception {
        return body(ok(as(userId, get("/api/v1/profiles/" + profileId + "/progress"))));
    }

    private static String earnedAt(String progress, String code) {
        List<String> at = JsonPath.read(progress, "$.achievements[?(@.code == '" + code + "')].earnedAt");
        assertThat(at).as("업적 %s 가 목록에 있다", code).hasSize(1);
        return at.getFirst();
    }

    private static void assertEarned(String progress, String code, boolean earned) {
        String at = earnedAt(progress, code);
        if (earned) assertThat(at).as("업적 %s 를 받았다", code).isNotNull();
        else assertThat(at).as("업적 %s 를 아직 받지 않았다", code).isNull();
    }

    private MockHttpServletRequestBuilder as(String userId, MockHttpServletRequestBuilder request) {
        return request.header("X-Dev-User-Id", userId).contentType(MediaType.APPLICATION_JSON);
    }

    private MvcResult ok(MockHttpServletRequestBuilder request) throws Exception {
        return expect(request, 200);
    }

    private MvcResult created(MockHttpServletRequestBuilder request) throws Exception {
        return expect(request, 201);
    }

    private MvcResult expect(MockHttpServletRequestBuilder request, int code) throws Exception {
        MvcResult res = mvc.perform(request).andReturn();
        assertThat(res.getResponse().getStatus())
                .as("%s %s → %s", res.getRequest().getMethod(), res.getRequest().getRequestURI(), body(res))
                .isEqualTo(code);
        return res;
    }

    private static String body(MvcResult res) throws Exception {
        return res.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    private static <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    /** 움직일 수 있는 시계. {@link #withZone} 으로 만든 시계도 같은 시각을 따라간다. */
    static final class MovableClock extends Clock {
        private final Instant[] now;
        private final ZoneId zone;

        MovableClock(Instant start) {
            this(new Instant[] {start}, ZoneId.of("UTC"));
        }

        private MovableClock(Instant[] now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void set(Instant value) {
            now[0] = value;
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
