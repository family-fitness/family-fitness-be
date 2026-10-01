package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunExecutorConfig;
import kr.ac.kookmin.familyfitness.support.DeletionFailureSwitch;
import kr.ac.kookmin.familyfitness.support.Leftovers;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 API 로 가족 기록을 쌓는 시험의 바탕. 기록을 지우는 시험(탈퇴와 구성원 내보내기 {@link AccountDeletionApiTestBase}, 동의 철회
 * {@link ConsentWithdrawalApiTestBase})이 이 클래스를 상속하고, 지운 사람을 가리키는 행이 DB 어디에 남는지 information_schema 로
 * 모든 표를 훑어 본다({@link Leftovers}).
 *
 * <p>가족은 대부분 심사용 계정 로그인으로 만든다. 체험 가족(엄마, 아빠, 하윤, 서준)에 지난 2주 미션, 칸 끝, 응원, 알림, 경험치,
 * 업적, 쉬는 날 카드, 측정, 운동할 수 있는 시간, 동의 이력이 들어 있다. INVITED 로 들어가면 심사위원이 아빠 자리에 붙고, 오너인
 * 엄마는 아무도 로그인하지 않는 계정이라 시험이 토큰을 직접 만든다. AI 는 스텁이고, 편성은 같은 스레드에서 끝까지 돈다.
 */
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {"app.auth.review-login.enabled=true", "app.coach.poll-interval-ms=0", "app.coach.max-polls=3"})
abstract class FamilyApiTestBase {
    /**
     * 심사용 계정 로그인에 쓰는 IP 번호. 같은 IP 가 한 시간 안에 같은 kind 로 다시 부르면 먼저 만든 계정과 가족을 다시 주므로, 이
     * 클래스를 상속한 시험 모두가 한 번호표를 나눠 쓴다.
     */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @TestBean(name = CoachRunExecutorConfig.EXECUTOR, methodName = "syncCoachRunExecutor")
    TaskExecutor coachRunTaskExecutor;

    static TaskExecutor syncCoachRunExecutor() {
        return new SyncTaskExecutor();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestAuth auth;

    @Autowired
    Leftovers leftovers;

    @Autowired
    DeletionFailureSwitch failure;

    final LocalDate today = LocalDate.now(SEOUL);

    /** 로그인한 계정. */
    record Session(UUID userId, String bearer, String refreshToken) {}

    /** 체험 가족. 이름 → 프로필 id. */
    record Family(UUID familyId, Map<String, UUID> ids) {
        UUID id(String name) {
            return ids.get(name);
        }
    }

    Session devLogin() throws Exception {
        String subject = "deletion-" + UUID.randomUUID();
        JsonNode body = read(mvc.perform(json(post("/api/v1/auth/dev-login"), Map.of("providerUserId", subject)))
                .andExpect(status().isOk()));
        return sessionOf(body);
    }

    /** 심사용 계정 로그인. IP 마다 한 시간 60번 한도가 있어 부를 때마다 다른 IP 로 부른다. */
    Session reviewLogin(String kind) throws Exception {
        return sessionOf(reviewLoginBody(kind));
    }

    JsonNode reviewLoginBody(String kind) throws Exception {
        int n = NEXT_IP.getAndIncrement();
        String ip = "10.88." + (n / 250) + "." + (n % 250 + 1);
        return read(mvc.perform(json(post("/api/v1/auth/review-login"), Map.of("kind", kind))
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        }))
                .andExpect(status().isOk()));
    }

    /** INVITED 로 들어가 체험 가족의 아빠 자리에 붙은 심사위원. 오너(엄마)는 따로 있는 계정이다. */
    Session invitedDad() throws Exception {
        JsonNode body = reviewLoginBody("INVITED");
        Session dad = sessionOf(body);
        mvc.perform(as(
                        dad,
                        json(
                                post("/api/v1/profiles/claim"),
                                Map.of("claimCode", body.get("inviteCode").asString()))))
                .andExpect(status().isOk());
        return dad;
    }

    /** 가족 초대를 내고 코드를 돌려준다. CHILD 는 보호자 동의를 둘 다 true 로 함께 보낸다. */
    String createFamilyInvite(Session session, UUID familyId, String role) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", role);
        if (role.equals("CHILD")) body.put("guardianConsent", Map.of("personalData", true, "healthData", true));
        return read(mvc.perform(as(session, json(post("/api/v1/families/" + familyId + "/invites"), body)))
                        .andExpect(status().isCreated()))
                .get("code")
                .asString();
    }

    /** 가족 초대코드로 들어와 만든 프로필 id. */
    UUID joinByFamilyInvite(Session session, String code, String name, String birthDate) throws Exception {
        return uuid(read(mvc.perform(as(session, json(post("/api/v1/profiles/claim"), joinBody(code, name, birthDate))))
                        .andExpect(status().isOk()))
                .get("profileId"));
    }

    static Map<String, Object> joinBody(String code, String name, String birthDate) {
        return Map.of("claimCode", code, "name", name, "birthDate", birthDate, "sex", "M");
    }

    Session sessionOf(JsonNode body) {
        return new Session(
                uuid(body.get("userId")),
                "Bearer " + body.get("accessToken").asString(),
                body.get("refreshToken").asString());
    }

    /** 이 계정으로 시험이 직접 만든 액세스 토큰. 아무도 로그인하지 않는 체험 가족 오너에게 쓴다. */
    Session bearerSession(UUID userId) {
        return new Session(userId, auth.bearer(userId), "");
    }

    UUID ownerUserOf(UUID familyId) {
        return jdbc.queryForObject(
                "select user_id from profiles where family_id = ? and is_owner = true", UUID.class, familyId);
    }

    Family familyOf(Session session) throws Exception {
        JsonNode me = read(mvc.perform(as(session, get("/api/v1/me"))).andExpect(status().isOk()));
        UUID familyId = uuid(me.get("profiles").get(0).get("familyId"));
        JsonNode members = read(mvc.perform(as(session, get("/api/v1/families/" + familyId + "/profiles")))
                .andExpect(status().isOk()));
        Map<String, UUID> ids = new LinkedHashMap<>();
        members.get("profiles").forEach(it -> ids.put(it.get("name").asString(), uuid(it.get("profileId"))));
        return new Family(familyId, ids);
    }

    JsonNode profileIn(UUID familyId, UUID profileId) throws Exception {
        Session owner = bearerSession(ownerUserOf(familyId));
        JsonNode members = read(mvc.perform(as(owner, get("/api/v1/families/" + familyId + "/profiles")))
                .andExpect(status().isOk()));
        return list(members.get("profiles")).stream()
                .filter(it -> uuid(it.get("profileId")).equals(profileId))
                .findFirst()
                .orElseThrow();
    }

    /** 가족, 식구 프로필, 식구 계정 id. */
    List<UUID> everyone(Family family, Session session) {
        List<UUID> ids = new ArrayList<>();
        ids.add(family.familyId());
        ids.addAll(family.ids().values());
        ids.add(session.userId());
        ids.addAll(
                idsOf("select user_id from profiles where family_id = ? and user_id is not null", family.familyId()));
        return ids.stream().distinct().toList();
    }

    UUID createMission(Session session, UUID familyId, String title, List<UUID> participants) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("startDate", today);
        body.put("endDate", today);
        body.put("targetMetric", "TIMER_MINUTES");
        body.put("targetValue", 2);
        body.put("participantProfileIds", participants);
        return uuid(read(mvc.perform(as(session, json(post("/api/v1/families/" + familyId + "/missions"), body)))
                        .andExpect(status().isCreated()))
                .get("missionId"));
    }

    /** 칸 없는 미션은 칸 1 이 미션 전체다. 2분 목표에 2분을 채워 끝낸다. */
    void complete(Session session, UUID missionId, UUID profileId) throws Exception {
        completeRequest(session, missionId, profileId).andExpect(status().isOk());
    }

    ResultActions completeRequest(Session session, UUID missionId, UUID profileId) throws Exception {
        Instant end = Instant.now().minusSeconds(5);
        return mvc.perform(as(
                session,
                json(
                        post("/api/v1/missions/" + missionId + "/sessions/1/complete"),
                        Map.of(
                                "profileId",
                                profileId,
                                "activeSeconds",
                                120,
                                "startedAt",
                                end.minusSeconds(130),
                                "endedAt",
                                end))));
    }

    UUID cheer(
            Session session,
            UUID familyId,
            UUID from,
            UUID to,
            String kind,
            @Nullable String sticker,
            @Nullable UUID replyTo)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromProfileId", from);
        body.put("toProfileId", to);
        body.put("kind", kind);
        body.put("message", "수고했어");
        if (sticker != null) body.put("stickerId", sticker);
        if (replyTo != null) body.put("replyToCheerId", replyTo);
        return uuid(read(mvc.perform(as(session, json(post("/api/v1/families/" + familyId + "/cheers"), body)))
                        .andExpect(status().isCreated()))
                .get("cheerId"));
    }

    /** 오늘 편성을 짜고 승인한다. withParent 면 요청한 보호자가 함께 한다. 편성 id. */
    UUID planAndApprove(Session session, UUID familyId, UUID subject, boolean withParent) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profileId", subject);
        body.put("date", today);
        body.put("minutes", 20);
        body.put("withParent", withParent);
        UUID run = uuid(read(mvc.perform(as(session, json(post("/api/v1/families/" + familyId + "/coach/runs"), body)))
                        .andExpect(status().isAccepted()))
                .get("coachRunId"));
        mvc.perform(as(session, get("/api/v1/coach/runs/" + run)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"));
        JsonNode approved = read(mvc.perform(as(session, post("/api/v1/coach/runs/" + run + "/approve")))
                .andExpect(status().isOk()));
        assertThat(approved.get("createdMissions").size()).isPositive();
        return run;
    }

    /** 그 사람 이름으로 남기는 기록: 구간 찜, 영상 찜, 코치 대화. */
    void personalTouches(Session session, UUID profileId) throws Exception {
        String clip = jdbc.queryForObject(
                "select clip_id from video_exercises where active = true order by clip_id limit 1", String.class);
        mvc.perform(as(
                        session,
                        json(
                                post("/api/v1/exercises/" + clip + "/favorite"),
                                Map.of("profileId", profileId, "favorited", true))))
                .andExpect(status().isOk());
        String video =
                jdbc.queryForObject("select video_id from exercise_videos order by video_id limit 1", String.class);
        mvc.perform(as(
                        session,
                        json(
                                post("/api/v1/videos/" + video + "/favorite"),
                                Map.of("profileId", profileId, "favorited", true))))
                .andExpect(status().isOk());
        mvc.perform(as(
                        session,
                        json(
                                post("/api/v1/coach/chat"),
                                Map.of("profileId", profileId, "question", "줄넘기는 얼마나 하면 좋아요?"))))
                .andExpect(status().isOk());
    }

    UUID todaysMissionOf(Session session, UUID familyId, UUID profileId) throws Exception {
        JsonNode missions = read(mvc.perform(as(session, get("/api/v1/families/" + familyId + "/missions")))
                .andExpect(status().isOk()));
        return list(missions.get("missions")).stream()
                .filter(it -> it.get("startDate").asString().equals(today.toString()))
                .filter(it -> list(it.get("participants")).stream()
                        .anyMatch(p -> uuid(p.get("profileId")).equals(profileId)
                                && !p.get("completed").asBoolean()))
                .map(it -> uuid(it.get("missionId")))
                .findFirst()
                .orElseThrow();
    }

    JsonNode progress(Session session, UUID profileId) throws Exception {
        return read(mvc.perform(as(session, get("/api/v1/profiles/" + profileId + "/progress")))
                .andExpect(status().isOk()));
    }

    /** 경험치 합계, 레벨, 연속 기록, 운동한 날, 업적과 받은 시각이 같다. 최근 줄은 보낸 사람 칸이 비므로 보지 않는다. */
    static void assertSameProgress(JsonNode after, JsonNode before) {
        for (String field : List.of("xp", "level", "levelFloorXp", "streakDays", "activeDays")) {
            assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
        }
        assertThat(after.get("achievements")).isEqualTo(before.get("achievements"));
        assertThat(before.get("xp").asInt()).isPositive();
    }

    Map<String, Object> ownerBody() {
        return Map.of("familyName", "지울 가족", "owner", Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "F"));
    }

    static Map<String, Object> slots() {
        return Map.of(
                "slots",
                List.of(
                        Map.of("day", "MON", "start", "19:00", "minutes", 20),
                        Map.of("day", "SAT", "start", "10:00", "minutes", 30)));
    }

    static String fitnessBody(LocalDate on, Map<String, String> items) {
        StringBuilder sb = new StringBuilder("{\"testedOn\":\"" + on + "\",\"source\":\"SELF_INPUT\"");
        sb.append(",\"heightCm\":162,\"weightKg\":55,\"items\":[");
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

    List<UUID> idsOf(String sql, Object... args) {
        return jdbc.queryForList(sql, UUID.class, args);
    }

    long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    static UUID uuid(JsonNode node) {
        return UUID.fromString(node.asString());
    }
}
