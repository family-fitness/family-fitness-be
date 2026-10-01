package kr.ac.kookmin.familyfitness.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import kr.ac.kookmin.familyfitness.league.application.LeagueSettlementScheduler;
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
 * 여정 A 서준이네 — 엄마(만든 사람) · 서준 11(계정 없음) · 아빠(D2 초대코드로 합류, 주말만).
 * D1(9/30) ~ D7(10/6) 매일 서준 하루 편성 → 승인 → 칸을 모두 끝냄. 주말(D4 · D5)에는 아빠가 같이 한다.
 * 첫걸음, 3일 연속, 7일 연속, 주말에도, 가족과 함께, 누적 100분 업적과 07:30 알림, 10월 리그 방을 본다.
 * 시각을 마음대로 바꿀 수 있는 Clock 으로 바꾸고, 날을 넘길 때 예약 작업(07:30 알림 · 리그 정산)을 직접 부른다. AI 는 스텁이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-journey-a;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.auth.dev-auto-login.enabled=true",
            "app.coach.poll-interval-ms=0",
            "app.coach.max-polls=3"
        })
class AJourneyTest {
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

    @Autowired
    LeagueSettlementScheduler league;

    @Test
    @DisplayName("서준이네 일주일: 날마다 편성하고 끝내면 첫걸음, 3일 연속, 주말에도, 가족과 함께, 누적 100분, 7일 연속 업적이 차례로 붙는다")
    void 서준이네_일주일_정상_흐름() throws Exception {
        CLOCK.set(at(D1, 6, 0));
        String mom = devLogin("qa-A-mom");
        String familyId = createFamily(mom, "서준이네", "엄마", "1986-05-01", "F");
        String momProfile = selfProfile(mom);
        String seojun = addChild(mom, familyId, "서준", "2015-03-10", "M");

        // 서준: 유소년 일곱 종목 + 키 · 몸무게 · 허리둘레 → 종합 등급이 하나 나온다
        String measured = body(created(as(mom, post("/api/v1/profiles/" + seojun + "/fitness-tests"))
                .content(fitnessBody(
                        D1,
                        "145",
                        "38",
                        "60",
                        Map.of(
                                "020", "55", "028", "45.0", "009", "40", "012", "12.0", "043", "33", "022", "170",
                                "044", "20")))));
        assertThat((String) read(measured, "$.certification.status")).isEqualTo("GRADED");
        assertThat((String) read(measured, "$.certification.grade")).isNotNull();
        // 엄마 본인은 일부 종목만 잰다
        created(as(mom, post("/api/v1/profiles/" + momProfile + "/fitness-tests"))
                .content(fitnessBody(D1, "162", "55", null, Map.of("028", "40.0", "012", "10.0"))));

        String dad = null;
        String dadProfile = null;
        for (int day = 1; day <= 7; day++) {
            LocalDate date = D1.plusDays(day - 1);
            if (day > 1) {
                CLOCK.set(at(date, 0, 10));
                if (date.getDayOfMonth() == 1) league.run();
                CLOCK.set(at(date, 6, 0));
            }
            if (day == 2) {
                // 아빠 합류: 초대코드 → claim → 참여 방식 WEEKEND
                String dadProfileId = addParent(mom, familyId, "아빠", "1984-04-01", "M");
                String code = read(
                        body(created(as(mom, post("/api/v1/profiles/" + dadProfileId + "/invite")))), "$.claimCode");
                dad = devLogin("qa-A-dad");
                String claim =
                        body(ok(as(dad, post("/api/v1/profiles/claim")).content("{\"claimCode\":\"" + code + "\"}")));
                assertThat((String) read(claim, "$.nextStep")).isEqualTo("SUPPORT_MODE");
                ok(as(dad, patch("/api/v1/profiles/" + dadProfileId + "/support-mode"))
                        .content("{\"supportMode\":\"WEEKEND\"}"));
                dadProfile = dadProfileId;
            }
            boolean weekend = day == 4 || day == 5;
            String planner = weekend ? dad : mom;
            String missionId = planAndApprove(planner, familyId, seojun, date, weekend);

            // 07:30 알림: 오늘 잡힌 운동이 서준 알림함에 온다
            CLOCK.set(at(date, 7, 30));
            notifications.missionReady();
            CLOCK.set(at(date, 7, 31));
            List<String> readyDates = read(
                    body(ok(as(mom, get("/api/v1/notifications?profileId=" + seojun)))),
                    "$.items[?(@.kind == 'MISSION_READY')].date");
            assertThat(readyDates).as("D%d 07:30 MISSION_READY", day).contains(date.toString());

            CLOCK.set(at(date, 19, 0));
            completeAll(mom, missionId, seojun);

            String progress = progress(mom, seojun);
            assertThat((Integer) read(progress, "$.streakDays"))
                    .as("D%d 이어서 한 날", day)
                    .isEqualTo(day);
            if (weekend) {
                // 아이가 끝낸 칸은 같이 하기로 한 아빠에게도 적힌다
                List<Boolean> dadDone = read(
                        body(ok(as(mom, get("/api/v1/missions/" + missionId)))),
                        "$.participants[?(@.profileId == '" + dadProfile + "')].completed");
                assertThat(dadDone).as("D%d 아빠도 같이 끝냄", day).containsExactly(true);
            }
            assertEarned(progress, "FIRST_STEP", true);
            assertEarned(progress, "STREAK_3", day >= 3);
            assertEarned(progress, "WEEKEND", day >= 4);
            assertEarned(progress, "TOGETHER", day >= 4);
            assertEarned(progress, "MIN_100", day >= 5); // 20분 × 5일
            assertEarned(progress, "STREAK_7", day >= 7);
        }

        // 리그: 10월 방에 들어가 있고, 8가족 미만이라 오르내림이 없다
        String standing = body(ok(as(mom, get("/api/v1/families/" + familyId + "/league?month=2026-10"))));
        assertThat((String) read(standing, "$.month")).isEqualTo("2026-10");
        assertThat((Integer) read(standing, "$.promote")).isZero();
        assertThat((Integer) read(standing, "$.demote")).isZero();
        assertThat((Integer) read(standing, "$.rate")).isEqualTo(100);
    }

    @Test
    @DisplayName("서준이네 둘째 주: 운동이 잡히지 않은 날은 이어서 한 날을 끊지 않고, 이미 받은 업적은 다시 주지 않는다")
    void 서준이네_둘째_주_띄엄띄엄() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 7);
        CLOCK.set(at(start, 6, 0));
        String mom = devLogin("qa-A2-mom");
        String familyId = createFamily(mom, "서준이네둘째주", "엄마", "1986-05-01", "F");
        String seojun = addChild(mom, familyId, "서준", "2015-03-10", "M");
        created(as(mom, post("/api/v1/profiles/" + seojun + "/fitness-tests"))
                .content(fitnessBody(start, "145", "38", "60", Map.of("012", "12.0", "028", "45.0"))));

        // 사흘 이어서 한다
        for (int i = 0; i < 3; i++) {
            LocalDate date = start.plusDays(i);
            CLOCK.set(at(date, 19, 0));
            completeAll(mom, planAndApprove(mom, familyId, seojun, date, false), seojun);
        }
        String before = progress(mom, seojun);
        assertThat((Integer) read(before, "$.streakDays")).isEqualTo(3);
        String streak3At = earnedAt(before, "STREAK_3");

        // 사흘에 한 번만 한다 — 잡힌 날은 모두 했으니 이어진다
        for (int i = 5; i <= 11; i += 3) {
            LocalDate date = start.plusDays(i);
            CLOCK.set(at(date, 19, 0));
            completeAll(mom, planAndApprove(mom, familyId, seojun, date, false), seojun);
        }
        String after = progress(mom, seojun);
        assertThat((Integer) read(after, "$.streakDays")).isEqualTo(6);
        assertThat(earnedAt(after, "STREAK_3")).as("3일 연속 업적은 처음 받은 때 그대로").isEqualTo(streak3At);
        assertEarned(after, "STREAK_7", false);

        // 잡힌 날을 빼먹으면 끊긴다
        LocalDate missed = start.plusDays(12);
        CLOCK.set(at(missed, 6, 0));
        planAndApprove(mom, familyId, seojun, missed, false);
        CLOCK.set(at(missed.plusDays(1), 19, 0));
        assertThat((Integer) read(progress(mom, seojun), "$.streakDays")).isZero();
    }

    // ---- 도우미 ----

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
