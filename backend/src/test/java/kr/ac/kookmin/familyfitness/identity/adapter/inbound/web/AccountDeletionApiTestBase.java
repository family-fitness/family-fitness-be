package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunExecutorConfig;
import kr.ac.kookmin.familyfitness.support.DeletionFailureSwitch;
import kr.ac.kookmin.familyfitness.support.Leftovers;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * 탈퇴(DELETE /me)와 구성원 내보내기(DELETE /families/{familyId}/profiles/{profileId}). 실제 API 로 기록을 쌓은 뒤 지우고,
 * 지운 사람을 가리키는 행이 DB 어디에도 남지 않는지 information_schema 로 모든 표를 훑어 본다({@link Leftovers}).
 * H2 용 시험과 PostgreSQL 용 시험이 이 클래스를 상속한다.
 *
 * <p>가족은 대부분 심사용 계정 로그인으로 만든다. 체험 가족(엄마, 아빠, 하윤, 서준)에 지난 2주 미션, 칸 끝, 응원, 알림, 경험치,
 * 업적, 쉬는 날 카드, 측정, 운동할 수 있는 시간, 동의 이력이 들어 있다. INVITED 로 들어가면 심사위원이 아빠 자리에 붙고, 오너인
 * 엄마는 아무도 로그인하지 않는 계정이라 시험이 토큰을 직접 만든다. AI 는 스텁이고, 편성은 같은 스레드에서 끝까지 돈다.
 */
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {"app.auth.review-login.enabled=true", "app.coach.poll-interval-ms=0", "app.coach.max-polls=3"})
abstract class AccountDeletionApiTestBase {
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

    // ===== 탈퇴 =====

    @Test
    @DisplayName("가족 없는 계정이 탈퇴하면 계정과 리프레시 토큰 기록이 지워지고, 옛 토큰은 401 이다")
    void 가족_없는_계정은_계정만_지워진다() throws Exception {
        Session fresh = devLogin();

        withdraw(fresh).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(fresh.userId()))).isEmpty();
        expectGoneAccount(fresh);
        mvc.perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", fresh.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("혼자 남은 오너가 탈퇴하면 가족과 모든 기록이 지워져, 지운 id 를 가리키는 행이 어느 표에도 없다")
    void 혼자_남은_오너가_탈퇴하면_가족까지_지워진다() throws Exception {
        Session mom = devLogin();
        JsonNode created = read(mvc.perform(as(mom, json(post("/api/v1/families"), ownerBody())))
                .andExpect(status().isCreated()));
        UUID familyId = uuid(created.get("familyId"));
        UUID momId = uuid(created.get("ownerProfile").get("profileId"));

        mvc.perform(as(mom, json(post("/api/v1/families/" + familyId + "/rest-cards"), Map.of("date", today))))
                .andExpect(status().isCreated());
        mvc.perform(as(mom, json(put("/api/v1/profiles/" + momId + "/availability"), slots())))
                .andExpect(status().isOk());
        mvc.perform(as(
                        mom,
                        post("/api/v1/profiles/" + momId + "/fitness-tests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fitnessBody(today, Map.of("012", "10.0", "028", "40.0")))))
                .andExpect(status().isCreated());
        UUID mission = createMission(mom, familyId, "엄마 운동", List.of(momId));
        complete(mom, mission, momId);
        mvc.perform(as(
                        mom,
                        json(
                                post("/api/v1/missions/" + mission + "/feedback"),
                                Map.of("profileId", momId, "feel", "GOOD"))))
                .andExpect(status().isNoContent());
        personalTouches(mom, momId);
        mvc.perform(as(mom, get("/api/v1/families/" + familyId + "/league"))).andExpect(status().isOk());
        assertThat(leftovers.referencing(List.of(familyId, momId, mom.userId())))
                .containsKeys(
                        "activity_daily.profile_id",
                        "coach_messages.profile_id",
                        "exercise_favorites.profile_id",
                        "fitness_tests.profile_id",
                        "league_members.family_id",
                        "mission_feedback.profile_id",
                        "mission_session_completions.profile_id",
                        "progress_achievements.profile_id",
                        "progress_xp_events.profile_id",
                        "profile_availability_slots.created_by",
                        "rest_cards.created_by",
                        "video_interactions.profile_id");

        withdraw(mom).andExpect(status().isNoContent());

        List<UUID> gone = List.of(familyId, momId, mom.userId(), mission);
        assertThat(leftovers.referencing(gone)).isEmpty();
        assertThat(leftovers.mentioning(gone)).isEmpty();
        expectGoneAccount(mom);
    }

    @Test
    @DisplayName("오너는 가족에 다른 프로필이 있으면 탈퇴하지 못한다. 409 FAMILY_NOT_EMPTY 이고 아무 행도 줄지 않는다")
    void 오너는_구성원이_남아_있으면_탈퇴하지_못한다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        Map<String, Long> before = leftovers.referencing(everyone(family, mom));

        withdraw(mom)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_EMPTY"));

        assertThat(leftovers.referencing(everyone(family, mom))).isEqualTo(before);
        mvc.perform(as(mom, get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("HOME"));
    }

    @Test
    @DisplayName("오너가 아닌 보호자가 탈퇴하면 그 사람과 계정만 지워지고 가족과 아이의 기록, 경험치, 연속 기록은 그대로다")
    void 오너가_아닌_보호자가_탈퇴하면_그_사람만_지워진다() throws Exception {
        Session dad = invitedDad();
        Family family = familyOf(dad);
        UUID familyId = family.familyId();
        UUID momId = family.id("엄마");
        UUID dadId = family.id("아빠");
        UUID hayun = family.id("하윤");
        UUID seojun = family.id("서준");

        // 아빠가 남긴 기록: 하윤 미션을 만들고, 혼자 하는 미션과 하윤과 함께 하는 미션을 끝내고, 하윤에게 칭찬 스티커를 보낸다
        UUID forHayun = createMission(dad, familyId, "하윤 줄넘기", List.of(hayun));
        UUID dadAlone = createMission(dad, familyId, "아빠 운동", List.of(dadId));
        complete(dad, dadAlone, dadId);
        UUID together = createMission(dad, familyId, "아빠와 하윤", List.of(dadId, hayun));
        complete(dad, together, dadId);
        UUID praise = cheer(dad, familyId, dadId, hayun, "PRAISE", "star", null);
        cheer(dad, familyId, hayun, dadId, "THANKS", "heart", praise);
        // 서준 편성을 아빠가 요청하고 승인했다(아빠가 함께 한다)
        UUID run = planAndApprove(dad, familyId, seojun, true);
        // 아빠가 서준의 운동할 수 있는 시간을 고쳐 적고, 동의를 다시 하고, 하윤 초대코드를 보냈다
        mvc.perform(as(dad, json(put("/api/v1/profiles/" + seojun + "/availability"), slots())))
                .andExpect(status().isOk());
        mvc.perform(as(
                        dad,
                        json(
                                patch("/api/v1/profiles/" + seojun + "/consent"),
                                Map.of("personalData", true, "healthData", true))))
                .andExpect(status().isOk());
        mvc.perform(as(dad, post("/api/v1/profiles/" + hayun + "/invite"))).andExpect(status().isCreated());
        // 이 달에 쓴 쉬는 날 카드를 아빠가 쓴 것으로 둔다(오늘은 서준이 이미 움직여 새로 쓸 수 없다)
        jdbc.update("update rest_cards set created_by = ? where family_id = ?", dadId, familyId);
        personalTouches(dad, dadId);
        mvc.perform(as(dad, get("/api/v1/families/" + familyId + "/league"))).andExpect(status().isOk());
        JsonNode hayunBefore = progress(dad, hayun);
        JsonNode seojunBefore = progress(dad, seojun);
        assertThat(leftovers.referencing(List.of(dadId, dad.userId())))
                .containsKeys(
                        "coach_runs.approved_by",
                        "coach_runs.requested_by_profile_id",
                        "consent_events.actor_user_id",
                        "missions.created_by",
                        "profile_availability_slots.created_by",
                        "profiles.claim_code_issued_by",
                        "profiles.consent_by_user_id",
                        "progress_xp_events.from_profile_id",
                        "rest_cards.created_by");

        withdraw(dad).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(dadId, dad.userId()))).isEmpty();
        assertThat(jdbc.queryForList(
                        "select participants_json from coach_run_proposal_items where coach_run_id = ?",
                        String.class,
                        run))
                .isNotEmpty()
                .noneMatch(it -> it.contains(dadId.toString()));
        // 가족과 남는 식구는 그대로다
        assertThat(familyOf(bearerSession(ownerUserOf(familyId))).ids().keySet())
                .containsExactly("엄마", "하윤", "서준");
        // 아빠가 만든 미션은 오너가 만든 것으로, 아빠 혼자 하던 미션은 통째로 지워진다
        assertThat(jdbc.queryForObject("select created_by from missions where id = ?", UUID.class, forHayun))
                .isEqualTo(momId);
        assertThat(count("select count(*) from missions where id = ?", dadAlone))
                .isZero();
        assertThat(count("select count(*) from mission_participants where mission_id = ?", together))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select approved_by from coach_runs where id = ?", UUID.class, run))
                .isEqualTo(momId);
        assertThat(jdbc.queryForObject("select created_by from rest_cards where family_id = ?", UUID.class, familyId))
                .isEqualTo(momId);
        // 동의는 그대로 살아 있다
        assertThat(profileIn(familyId, seojun).get("consentGiven").asBoolean()).isTrue();
        // 아이의 경험치 합계, 레벨, 연속 기록, 운동한 날, 업적이 그대로다
        Session owner = bearerSession(ownerUserOf(familyId));
        assertSameProgress(progress(owner, hayun), hayunBefore);
        assertSameProgress(progress(owner, seojun), seojunBefore);
        expectGoneAccount(dad);
    }

    @Test
    @DisplayName("아이 본인 계정이 탈퇴하면 아이 프로필과 아이의 기록, 계정이 지워지고 가족은 남는다")
    void 아이_본인_계정이_탈퇴하면_아이와_계정이_지워진다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID hayun = family.id("하윤");
        String code = read(mvc.perform(as(mom, post("/api/v1/profiles/" + hayun + "/invite")))
                        .andExpect(status().isCreated()))
                .get("claimCode")
                .asString();
        Session kid = devLogin();
        mvc.perform(as(kid, json(post("/api/v1/profiles/claim"), Map.of("claimCode", code))))
                .andExpect(status().isOk());
        UUID todays = todaysMissionOf(mom, family.familyId(), hayun);
        complete(kid, todays, hayun);
        cheer(kid, family.familyId(), hayun, family.id("엄마"), "DONE", null, null);
        personalTouches(kid, hayun);
        JsonNode seojunBefore = progress(mom, family.id("서준"));

        withdraw(kid).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(hayun, kid.userId()))).isEmpty();
        assertThat(familyOf(mom).ids().keySet()).containsExactly("엄마", "아빠", "서준");
        assertSameProgress(progress(mom, family.id("서준")), seojunBefore);
        expectGoneAccount(kid);
    }

    // ===== 구성원 내보내기 =====

    @Test
    @DisplayName("오너가 아이를 내보내면 아이 프로필과 아이의 기록이 지워지고, 남는 아이의 경험치와 연속 기록은 그대로다")
    void 오너가_아이를_내보낸다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID seojun = family.id("서준");
        JsonNode hayunBefore = progress(mom, family.id("하윤"));

        remove(mom, family.familyId(), seojun).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(seojun))).isEmpty();
        assertThat(familyOf(mom).ids().keySet()).containsExactly("엄마", "아빠", "하윤");
        assertSameProgress(progress(mom, family.id("하윤")), hayunBefore);
    }

    @Test
    @DisplayName("오너가 계정 있는 보호자를 내보내면 프로필과 기록은 지워지고 계정은 남아 가족 없는 계정(CREATE_FAMILY)이 된다")
    void 오너가_계정_있는_보호자를_내보낸다() throws Exception {
        Session dad = invitedDad();
        Family family = familyOf(dad);
        UUID dadId = family.id("아빠");
        UUID hayun = family.id("하윤");
        UUID forHayun = createMission(dad, family.familyId(), "하윤 줄넘기", List.of(hayun));
        cheer(dad, family.familyId(), dadId, hayun, "PRAISE", "star", null);
        Session owner = bearerSession(ownerUserOf(family.familyId()));

        remove(owner, family.familyId(), dadId).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(dadId))).isEmpty();
        assertThat(count("select count(*) from users where id = ?", dad.userId()))
                .isEqualTo(1);
        mvc.perform(as(dad, get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("CREATE_FAMILY"))
                .andExpect(jsonPath("$.profiles").isEmpty())
                .andExpect(jsonPath("$.selfProfileId").isEmpty());
        mvc.perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", dad.refreshToken())))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select created_by from missions where id = ?", UUID.class, forHayun))
                .isEqualTo(family.id("엄마"));
        assertThat(familyOf(owner).ids().keySet()).containsExactly("엄마", "하윤", "서준");
    }

    @Test
    @DisplayName("편성 대상인 아이를 내보내면 편성은 지워지고, 함께 한 보호자의 미션은 직접 만든 미션으로 남는다")
    void 편성_대상을_내보내면_함께_한_보호자의_미션은_남는다() throws Exception {
        Session dad = invitedDad();
        Family family = familyOf(dad);
        UUID dadId = family.id("아빠");
        UUID seojun = family.id("서준");
        UUID run = planAndApprove(dad, family.familyId(), seojun, true);
        UUID mission = jdbc.queryForObject("select id from missions where coach_run_id = ?", UUID.class, run);
        complete(dad, mission, dadId);
        Session owner = bearerSession(ownerUserOf(family.familyId()));

        remove(owner, family.familyId(), seojun).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(seojun, run))).isEmpty();
        JsonNode left =
                read(mvc.perform(as(dad, get("/api/v1/missions/" + mission))).andExpect(status().isOk()));
        assertThat(left.get("origin").asString()).isEqualTo("MANUAL");
        assertThat(left.get("coachRunId").isNull()).isTrue();
        assertThat(list(left.get("participants"))).singleElement().satisfies(it -> {
            assertThat(uuid(it.get("profileId"))).isEqualTo(dadId);
            assertThat(it.get("doneSessions").size()).isPositive();
        });
    }

    @Test
    @DisplayName(
            "내보내기: 오너가 아니면 403 NOT_FAMILY_OWNER, 다른 가족이면 403 NOT_SAME_FAMILY, 이 가족 프로필이 아니면 404, 자기 프로필이면 409 CANNOT_REMOVE_SELF")
    void 내보내기는_오너만_다른_식구에게만_한다() throws Exception {
        Session dad = invitedDad();
        Family family = familyOf(dad);
        UUID familyId = family.familyId();
        Session owner = bearerSession(ownerUserOf(familyId));
        Session stranger = devLogin();
        Session otherMom = reviewLogin("FAMILY");
        UUID otherKid = familyOf(otherMom).id("하윤");
        Map<String, Long> before = leftovers.referencing(everyone(family, dad));

        remove(dad, familyId, family.id("하윤"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_FAMILY_OWNER"));
        remove(stranger, familyId, family.id("하윤"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        remove(owner, familyId, otherKid)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));
        remove(owner, familyId, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));
        remove(owner, UUID.randomUUID(), family.id("하윤"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_FOUND"));
        remove(owner, familyId, family.id("엄마"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANNOT_REMOVE_SELF"));

        assertThat(leftovers.referencing(everyone(family, dad))).isEqualTo(before);
        assertThat(familyOf(owner).ids()).hasSize(4);
        assertThat(familyOf(otherMom).ids()).containsKey("하윤");
    }

    @Test
    @DisplayName("체험 가족에서 오너가 구성원을 모두 내보낸 뒤 탈퇴하면, 가족에 관한 행이 어느 표에도 남지 않는다")
    void 체험_가족을_모두_내보내고_탈퇴하면_깨끗하다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID familyId = family.familyId();
        UUID momId = family.id("엄마");
        UUID hayun = family.id("하윤");
        planAndApprove(mom, familyId, hayun, true);
        personalTouches(mom, hayun);
        mvc.perform(as(mom, get("/api/v1/families/" + familyId + "/league"))).andExpect(status().isOk());
        Set<UUID> gone = new LinkedHashSet<>(everyone(family, mom));
        gone.addAll(idsOf("select id from missions where family_id = ?", familyId));
        gone.addAll(idsOf("select id from cheers where family_id = ?", familyId));
        gone.addAll(idsOf("select id from coach_runs where family_id = ?", familyId));
        gone.addAll(idsOf(
                "select t.id from fitness_tests t join profiles p on p.id = t.profile_id where p.family_id = ?",
                familyId));
        gone.addAll(idsOf(
                "select n.id from notifications n join profiles p on p.id = n.profile_id where p.family_id = ?",
                familyId));
        assertThat(leftovers.referencing(gone).keySet())
                .contains(
                        "activity_daily.profile_id",
                        "cheers.from_profile_id",
                        "coach_messages.profile_id",
                        "coach_run_proposal_items.coach_run_id",
                        "consent_events.profile_id",
                        "fitness_test_items.fitness_test_id",
                        "mission_session_completions.profile_id",
                        "mission_sessions.mission_id",
                        "notifications.profile_id",
                        "progress_achievements.profile_id",
                        "progress_xp_events.profile_id",
                        "rest_cards.family_id");

        for (String name : List.of("아빠", "하윤", "서준")) {
            remove(mom, familyId, family.id(name)).andExpect(status().isNoContent());
        }
        assertThat(familyOf(mom).ids().keySet()).containsExactly("엄마");
        withdraw(mom).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(gone)).isEmpty();
        assertThat(leftovers.mentioning(everyone(family, mom))).isEmpty();
        assertThat(count("select count(*) from profiles where id = ?", momId)).isZero();
        expectGoneAccount(mom);
    }

    @Test
    @DisplayName("지우는 도중에 실패하면 먼저 지운 다른 모듈의 행까지 모두 되돌아가 아무것도 지워지지 않는다")
    void 지우는_도중에_실패하면_아무것도_지워지지_않는다() throws Exception {
        Session dad = invitedDad();
        Family family = familyOf(dad);
        UUID familyId = family.familyId();
        UUID dadId = family.id("아빠");
        UUID mission = createMission(dad, familyId, "아빠 운동", List.of(dadId));
        complete(dad, mission, dadId);
        Session owner = bearerSession(ownerUserOf(familyId));
        Map<String, Long> before = leftovers.referencing(everyone(family, dad));
        Session alone = devLogin();
        mvc.perform(as(alone, json(post("/api/v1/families"), ownerBody()))).andExpect(status().isCreated());
        Family aloneFamily = familyOf(alone);

        try {
            failure.failNext();
            withdraw(dad)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
            failure.failNext();
            remove(owner, familyId, family.id("서준")).andExpect(status().isInternalServerError());
            failure.failNext();
            withdraw(alone).andExpect(status().isInternalServerError());
        } finally {
            failure.reset();
        }

        assertThat(leftovers.referencing(everyone(family, dad))).isEqualTo(before);
        assertThat(familyOf(owner).ids()).hasSize(4);
        assertThat(count("select count(*) from missions where id = ?", mission)).isEqualTo(1);
        mvc.perform(as(dad, get("/api/v1/me"))).andExpect(status().isOk());
        assertThat(familyOf(alone)).isEqualTo(aloneFamily);
    }

    // ===== 도우미 =====

    /** 계정이 지워졌다. 옛 액세스 토큰은 서명이 맞아도 401 이고, 500 이 나지 않는다. */
    void expectGoneAccount(Session session) throws Exception {
        assertThat(count("select count(*) from users where id = ?", session.userId()))
                .isZero();
        mvc.perform(as(session, get("/api/v1/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        withdraw(session)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(as(session, json(post("/api/v1/families"), ownerBody())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertThat(count("select count(*) from refresh_tokens where user_id = ?", session.userId()))
                .isZero();
    }

    ResultActions withdraw(Session session) throws Exception {
        return mvc.perform(as(session, delete("/api/v1/me")));
    }

    ResultActions remove(Session session, UUID familyId, UUID profileId) throws Exception {
        return mvc.perform(as(session, delete("/api/v1/families/" + familyId + "/profiles/" + profileId)));
    }

    Session devLogin() throws Exception {
        String subject = "deletion-" + UUID.randomUUID();
        JsonNode body = read(mvc.perform(json(post("/api/v1/auth/dev-login"), Map.of("providerUserId", subject)))
                .andExpect(status().isOk()));
        return sessionOf(body);
    }

    /** 심사용 계정 로그인. IP 마다 한 시간 30번 한도가 있어 부를 때마다 다른 IP 로 부른다. */
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
        Instant end = Instant.now().minusSeconds(5);
        mvc.perform(as(
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
                                        end))))
                .andExpect(status().isOk());
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
