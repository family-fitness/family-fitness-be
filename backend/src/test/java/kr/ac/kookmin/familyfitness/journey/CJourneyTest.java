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
import java.util.Set;
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
 * 여정 C 민서네 — 아빠(만든 사람, 참여 방식 CHEER_ONLY) · 민서 8(계정 없음).
 * 7~10세는 또래 기준이 없어 백분위 · 등급이 비어 있는 것(NO_CRITERIA)이 정상이다.
 * 쉬는 날 카드는 가족당 한 달 두 장(D4 · D5)이고 세 번째(D6)는 거절된다. 쉬는 날에는 07:30 알림이 없고, 이어서 한 날도 끊기지 않는다.
 * D8 · D9 는 잡힌 운동을 빼먹어 이어서 한 날이 끊긴다.
 * 시각을 마음대로 바꿀 수 있는 Clock 으로 바꾸고, 날을 넘길 때 예약 작업(07:30 알림)을 직접 부른다. AI 는 스텁이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-journey-c;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.auth.dev-auto-login.enabled=true",
            "app.coach.poll-interval-ms=0",
            "app.coach.max-polls=3"
        })
class CJourneyTest {
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
    @DisplayName("민서(8세)를 재면 또래 기준이 없어 백분위 · 등급이 비고(NO_CRITERIA), 그래도 하루 편성은 된다")
    void 민서_또래_기준_없음() throws Exception {
        CLOCK.set(at(D1, 6, 0));
        String dad = devLogin("qa-C1-dad");
        String familyId = createFamily(dad, "민서네하나", "아빠", "1981-03-01", "M");
        String minseo = addChild(dad, familyId, "민서", "2018-05-01", "F");

        String measured = body(created(as(dad, post("/api/v1/profiles/" + minseo + "/fitness-tests"))
                .content(fitnessBody(D1, "128", "26", "55", Map.of("012", "12.0", "028", "30.0")))));
        assertThat((String) read(measured, "$.certification.status")).isEqualTo("NO_CRITERIA");
        assertThat((Object) read(measured, "$.certification.grade")).isNull();
        List<Object> percentiles = read(measured, "$.items[*].percentile");
        assertThat(percentiles).hasSize(2).containsOnlyNulls();

        String latest = body(ok(as(dad, get("/api/v1/profiles/" + minseo + "/fitness-tests/latest"))));
        assertThat((String) read(latest, "$.testedOn")).isEqualTo(D1.toString());
        assertThat((String) read(latest, "$.certification.status")).isEqualTo("NO_CRITERIA");

        String missionId = planAndApprove(dad, familyId, minseo, D1, false);
        CLOCK.set(at(D1, 19, 0));
        completeAll(dad, missionId, minseo);
        assertThat((Integer) read(progress(dad, minseo), "$.streakDays")).isEqualTo(1);
    }

    @Test
    @DisplayName("민서네 열흘: 쉬는 날 카드는 두 장까지이고, 쉬는 날에는 07:30 알림이 없고 이어서 한 날도 이어지며, 빼먹은 날에는 끊긴다")
    void 민서네_쉬는_날과_빠진_날() throws Exception {
        CLOCK.set(at(D1, 6, 0));
        String dad = devLogin("qa-C-dad");
        String familyId = createFamily(dad, "민서네", "아빠", "1981-03-01", "M");
        String dadProfile = selfProfile(dad);
        ok(as(dad, patch("/api/v1/profiles/" + dadProfile + "/support-mode"))
                .content("{\"supportMode\":\"CHEER_ONLY\"}"));
        String minseo = addChild(dad, familyId, "민서", "2018-05-01", "F");
        created(as(dad, post("/api/v1/profiles/" + minseo + "/fitness-tests"))
                .content(fitnessBody(D1, "128", "26", "55", Map.of("012", "12.0", "028", "30.0"))));

        // D1 은 AI 편성, D2~D9 는 직접 짜기로 날마다 10분
        String d1Mission = planAndApprove(dad, familyId, minseo, D1, false);
        StringBuilder dates = new StringBuilder();
        for (int day = 2; day <= 9; day++) {
            if (dates.length() > 0) dates.append(',');
            dates.append('"').append(D1.plusDays(day - 1)).append('"');
        }
        String manual = body(created(as(dad, post("/api/v1/families/" + familyId + "/missions"))
                .content("{\"title\":\"민서 줄넘기\",\"dates\":[" + dates + "],\"targetMetric\":\"TIMER_MINUTES\","
                        + "\"targetValue\":10,\"participantProfileIds\":[\"" + minseo + "\"]}")));
        List<String> manualMissions = read(manual, "$.missions[*].missionId");
        assertThat(manualMissions).hasSize(8);

        Set<Integer> restDays = Set.of(4, 5);
        Set<Integer> moveDays = Set.of(1, 2, 3, 6, 7);
        Map<Integer, Integer> streakAt21 = Map.of(1, 1, 2, 2, 3, 3, 4, 3, 5, 3, 6, 4, 7, 5, 8, 5, 9, 0);

        for (int day = 1; day <= 10; day++) {
            LocalDate date = D1.plusDays(day - 1);
            CLOCK.set(at(date, 6, 0));
            if (day == 2) {
                // 10월 카드 두 장: D4 · D5. 세 번째(D6)는 409 NO_REST_CARD_LEFT
                String first = body(created(as(dad, post("/api/v1/families/" + familyId + "/rest-cards"))
                        .content("{\"date\":\"" + D1.plusDays(3) + "\"}")));
                assertThat((Integer) read(first, "$.left")).isEqualTo(1);
                String second = body(created(as(dad, post("/api/v1/families/" + familyId + "/rest-cards"))
                        .content("{\"date\":\"" + D1.plusDays(4) + "\"}")));
                assertThat((Integer) read(second, "$.left")).isZero();
                String third = body(expect(
                        as(dad, post("/api/v1/families/" + familyId + "/rest-cards"))
                                .content("{\"date\":\"" + D1.plusDays(5) + "\"}"),
                        409));
                assertThat((String) read(third, "$.error.code")).isEqualTo("NO_REST_CARD_LEFT");
                String cards = body(ok(as(dad, get("/api/v1/families/" + familyId + "/rest-cards?month=2026-10"))));
                List<String> days = read(cards, "$.days");
                assertThat(days)
                        .containsExactly(
                                D1.plusDays(3).toString(), D1.plusDays(4).toString());
            }

            // 07:30 알림: 잡힌 날에는 오고, 쉬는 날에는 오지 않는다
            CLOCK.set(at(date, 7, 30));
            notifications.missionReady();
            CLOCK.set(at(date, 7, 31));
            List<String> readyDates = read(
                    body(ok(as(dad, get("/api/v1/notifications?profileId=" + minseo)))),
                    "$.items[?(@.kind == 'MISSION_READY')].date");
            if (restDays.contains(day)) {
                assertThat(readyDates).as("D%d 쉬는 날 07:30 알림", day).doesNotContain(date.toString());
            } else if (day <= 9) {
                assertThat(readyDates).as("D%d 07:30 알림", day).contains(date.toString());
            }

            CLOCK.set(at(date, 19, 0));
            if (moveDays.contains(day)) {
                completeAll(dad, day == 1 ? d1Mission : manualMissions.get(day - 2), minseo);
            }
            if (day <= 9) {
                CLOCK.set(at(date, 21, 0));
                assertThat((Integer) read(progress(dad, minseo), "$.streakDays"))
                        .as("D%d 21:00 이어서 한 날", day)
                        .isEqualTo(streakAt21.get(day));
            }
        }
        // 잡힌 운동이 없는 D10 에도 끊긴 그대로다
        assertThat((Integer) read(progress(dad, minseo), "$.streakDays")).isZero();
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
