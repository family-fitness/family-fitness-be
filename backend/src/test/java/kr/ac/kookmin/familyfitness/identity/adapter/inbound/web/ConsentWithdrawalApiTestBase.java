package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * 보호자가 아이의 동의를 거두면(PATCH /profiles/{profileId}/consent 에 false 가 하나라도 있음) 같은 트랜잭션에서 그 아이의 기록을
 * 지운다. 프로필(이름, 생년월일, 성별, 가족)과 동의 이력은 남는다. 실제 API 로 기록을 쌓은 뒤 거두고, 그 아이를 가리키는 행이 DB 어디에
 * 남는지 information_schema 로 모든 표를 훑어 본다. 그래서 표가 늘어도 새 표의 행을 지우는 일을 잊으면 이 시험이 깨진다. H2 용 시험과
 * PostgreSQL 용 시험이 이 클래스를 상속한다.
 */
abstract class ConsentWithdrawalApiTestBase extends FamilyApiTestBase {
    /** 동의를 거둔 뒤에도 그 아이를 가리켜도 되는 칸. 프로필 행과 동의 이력뿐이다. */
    static final Set<String> KEPT = Set.of("profiles.id", "consent_events.profile_id");

    @Test
    @DisplayName("체험 가족에서 하윤의 동의를 거두면 하윤을 가리키는 행은 프로필과 동의 이력만 남고, 다른 식구의 기록은 그대로다")
    void 동의를_거두면_그_아이의_기록이_모두_지워진다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID familyId = family.familyId();
        UUID momId = family.id("엄마");
        UUID hayun = family.id("하윤");
        UUID seojun = family.id("서준");

        // 체험 가족의 지난 2주 기록 위에 하윤 기록을 더 쌓는다: 하윤만 하는 미션, 엄마와 함께 하는 미션과 느낌, 엄마가 함께 하는
        // 하윤 편성, 찜과 코치 대화, 칭찬 스티커와 고마워요
        UUID alone = createMission(mom, familyId, "하윤 줄넘기", List.of(hayun));
        complete(mom, alone, hayun);
        UUID together = createMission(mom, familyId, "엄마와 하윤", List.of(momId, hayun));
        complete(mom, together, momId);
        complete(mom, together, hayun);
        feel(mom, together, hayun);
        UUID run = planAndApprove(mom, familyId, hayun, true);
        UUID runMission =
                idsOf("select id from missions where coach_run_id = ?", run).getFirst();
        complete(mom, runMission, momId);
        personalTouches(mom, hayun);
        UUID praise = cheer(mom, familyId, momId, hayun, "PRAISE", "star", null);
        cheer(mom, familyId, hayun, momId, "THANKS", "heart", praise);

        assertThat(leftovers.referencing(List.of(hayun)))
                .containsKeys(
                        "activity_daily.profile_id",
                        "cheers.from_profile_id",
                        "cheers.to_profile_id",
                        "coach_messages.profile_id",
                        "coach_runs.subject_profile_id",
                        "consent_events.profile_id",
                        "exercise_favorites.profile_id",
                        "fitness_tests.profile_id",
                        "mission_feedback.profile_id",
                        "mission_participants.profile_id",
                        "mission_session_completions.profile_id",
                        "notifications.about_profile_id",
                        "notifications.profile_id",
                        "profile_availability_slots.profile_id",
                        "profiles.id",
                        "progress_achievements.profile_id",
                        "progress_xp_events.profile_id",
                        "video_interactions.profile_id");
        assertThat(leftovers.mentioning(List.of(hayun))).containsKey("coach_run_proposal_items.participants_json");
        // 하윤과 함께 사라져야 하는 행의 id: 측정, 편성, 하윤만 하던 미션, 응원, 알림, 코치 대화
        Set<UUID> gone = new LinkedHashSet<>();
        gone.addAll(idsOf("select id from fitness_tests where profile_id = ?", hayun));
        gone.addAll(idsOf("select id from coach_runs where subject_profile_id = ?", hayun));
        gone.addAll(soloMissionsOf(familyId, hayun));
        gone.addAll(idsOf("select id from cheers where from_profile_id = ? or to_profile_id = ?", hayun, hayun));
        gone.addAll(idsOf(
                "select id from notifications where profile_id = ? or about_profile_id = ? or from_profile_id = ?",
                hayun,
                hayun,
                hayun));
        gone.addAll(idsOf("select id from coach_messages where profile_id = ?", hayun));
        assertThat(gone).contains(run, alone);
        Map<String, Object> profileBefore = profileRow(hayun);
        assertThat(profileBefore.get("height_cm")).isNotNull();
        Map<String, Long> seojunBefore = leftovers.referencing(List.of(seojun));
        JsonNode seojunProgress = progress(mom, seojun);
        JsonNode momProgress = progress(mom, momId);

        JsonNode revoked = read(consent(mom, hayun, false, false).andExpect(status().isOk()));

        assertThat(revoked.get("consentGiven").asBoolean()).isFalse();
        assertThat(revoked.get("measurable").asBoolean()).isFalse();
        assertThat(leftovers.referencing(List.of(hayun))).containsOnlyKeys(KEPT);
        assertThat(leftovers.mentioning(List.of(hayun))).isEmpty();
        assertThat(leftovers.referencing(gone)).isEmpty();
        // 프로필은 그대로 가족에 남고, 프로필에 적어 둔 키와 몸무게만 비운다
        Map<String, Object> profileAfter = profileRow(hayun);
        for (String column : List.of("display_name", "birth_date", "sex", "family_id", "role", "user_id")) {
            assertThat(profileAfter.get(column)).as(column).isEqualTo(profileBefore.get(column));
        }
        assertThat(profileAfter.get("height_cm")).isNull();
        assertThat(profileAfter.get("weight_kg")).isNull();
        assertThat(familyOf(mom).ids().keySet()).containsExactly("엄마", "아빠", "하윤", "서준");
        // 동의 이력은 지우지 않고 철회 줄을 하나 더한다
        assertThat(consentKinds(hayun)).containsExactly("GRANTED", "REVOKED");
        // 함께 한 미션은 하윤 몫만 빠지고 엄마의 칸 끝은 남는다
        assertThat(idsOf("select profile_id from mission_participants where mission_id = ?", together))
                .containsExactly(momId);
        assertThat(count(
                        "select count(*) from mission_session_completions where mission_id = ? and profile_id = ?",
                        together,
                        momId))
                .isEqualTo(1);
        // 하윤 편성은 지워지고, 엄마가 함께 하던 편성 미션은 직접 만든 미션으로 남는다
        JsonNode left =
                read(mvc.perform(as(mom, get("/api/v1/missions/" + runMission))).andExpect(status().isOk()));
        assertThat(left.get("origin").asString()).isEqualTo("MANUAL");
        assertThat(left.get("coachRunId").isNull()).isTrue();
        assertThat(list(left.get("participants"))).singleElement().satisfies(it -> assertThat(uuid(it.get("profileId")))
                .isEqualTo(momId));
        // 다른 아이와 보호자의 기록은 그대로다
        assertThat(leftovers.referencing(List.of(seojun))).isEqualTo(seojunBefore);
        assertSameProgress(progress(mom, seojun), seojunProgress);
        assertSameProgress(progress(mom, momId), momProgress);
        // 하윤 화면은 빈 기록으로 열린다
        JsonNode hayunProgress = progress(mom, hayun);
        assertThat(hayunProgress.get("xp").asInt()).isZero();
        assertThat(hayunProgress.get("streakDays").asInt()).isZero();
        assertThat(hayunProgress.get("activeDays").asInt()).isZero();
        mvc.perform(as(mom, get("/api/v1/profiles/" + hayun + "/fitness-tests")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("동의를 거둔 동안은 새 측정과 미션이 422 CONSENT_REQUIRED 이고, 다시 동의하면 새 측정과 운동 기록이 저장된다")
    void 다시_동의하면_새_기록이_저장된다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID familyId = family.familyId();
        UUID hayun = family.id("하윤");
        consent(mom, hayun, false, false).andExpect(status().isOk());

        measure(mom, hayun)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        mvc.perform(as(mom, json(post("/api/v1/families/" + familyId + "/missions"), missionBody(hayun))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        assertThat(leftovers.referencing(List.of(hayun))).containsOnlyKeys(KEPT);

        JsonNode granted = read(consent(mom, hayun, true, true).andExpect(status().isOk()));

        assertThat(granted.get("consentGiven").asBoolean()).isTrue();
        assertThat(granted.get("measurable").asBoolean()).isTrue();
        measure(mom, hayun).andExpect(status().isCreated());
        UUID mission = createMission(mom, familyId, "다시 시작", List.of(hayun));
        complete(mom, mission, hayun);
        assertThat(progress(mom, hayun).get("xp").asInt()).isPositive();
        assertThat(leftovers.referencing(List.of(hayun)))
                .containsKeys(
                        "activity_daily.profile_id",
                        "fitness_tests.profile_id",
                        "mission_session_completions.profile_id",
                        "progress_xp_events.profile_id");
        assertThat(consentKinds(hayun)).containsExactly("GRANTED", "REVOKED", "GRANTED");
    }

    @ParameterizedTest(name = "personalData={0}, healthData={1}")
    @CsvSource({"false, true", "true, false"})
    @DisplayName("둘 가운데 하나만 거둬도 같은 철회로 보고 같은 기록을 지운다. 아이 본인 계정은 그대로 붙어 있다")
    void 하나만_거둬도_같은_기록을_지운다(boolean personalData, boolean healthData) throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID familyId = family.familyId();
        UUID hayun = family.id("하윤");
        // 하윤이 자기 계정으로 들어와 오늘 미션을 끝내고, 엄마에게 알리고, 찜과 코치 대화를 남긴다
        String code = read(mvc.perform(as(mom, post("/api/v1/profiles/" + hayun + "/invite")))
                        .andExpect(status().isCreated()))
                .get("claimCode")
                .asString();
        Session kid = devLogin();
        mvc.perform(as(kid, json(post("/api/v1/profiles/claim"), Map.of("claimCode", code))))
                .andExpect(status().isOk());
        complete(kid, todaysMissionOf(mom, familyId, hayun), hayun);
        cheer(kid, familyId, hayun, family.id("엄마"), "DONE", null, null);
        personalTouches(kid, hayun);
        assertThat(leftovers.referencing(List.of(hayun)))
                .containsKeys("cheers.from_profile_id", "mission_session_completions.profile_id");

        consent(mom, hayun, personalData, healthData)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentGiven").value(false));

        assertThat(leftovers.referencing(List.of(hayun))).containsOnlyKeys(KEPT);
        assertThat(leftovers.mentioning(List.of(hayun))).isEmpty();
        // 보호자가 보낸 값 그대로 이력에 남는다
        Map<String, Object> last = jdbc.queryForMap(
                "select kind, personal_data, health_data from consent_events where profile_id = ? order by id desc"
                        + " limit 1",
                hayun);
        assertThat(last.get("kind")).isEqualTo("REVOKED");
        assertThat(last.get("personal_data")).isEqualTo(personalData);
        assertThat(last.get("health_data")).isEqualTo(healthData);
        // 계정은 프로필에 붙은 채로 남아 로그인하면 빈 기록을 본다
        assertThat(leftovers.referencing(List.of(kid.userId())))
                .containsOnlyKeys("users.id", "refresh_tokens.user_id", "profiles.user_id");
        mvc.perform(as(kid, get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfProfileId").value(hayun.toString()));
        assertThat(progress(kid, hayun).get("xp").asInt()).isZero();
    }

    @Test
    @DisplayName("이번 달 리그 달성률은 지운 기록을 빼고 다시 세고, 정산한 지난달 결과는 그대로다")
    void 리그는_지운_기록을_빼고_다시_센다() throws Exception {
        Session mom = devLogin();
        UUID familyId = uuid(read(mvc.perform(as(mom, json(post("/api/v1/families"), ownerBody())))
                        .andExpect(status().isCreated()))
                .get("familyId"));
        UUID kid = addChild(mom, familyId, "막내");
        UUID mission = createMission(mom, familyId, "막내 줄넘기", List.of(kid));
        complete(mom, mission, kid);
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        settledLastMonth(familyId, lastMonth, 80);
        assertThat(league(mom, familyId, null).get("rate").asInt()).isEqualTo(100);
        JsonNode settledBefore = league(mom, familyId, lastMonth);
        assertThat(settledBefore.get("rate").asInt()).isEqualTo(80);

        consent(mom, kid, false, false).andExpect(status().isOk());
        consent(mom, kid, true, true).andExpect(status().isOk());

        // 다시 동의해도 지운 날은 돌아오지 않는다. 셀 날이 없으면 달성률은 0 이 아니라 null 이다
        assertThat(league(mom, familyId, null).get("rate").isNull()).isTrue();
        assertThat(league(mom, familyId, lastMonth)).isEqualTo(settledBefore);
    }

    @Test
    @DisplayName("지우는 도중에 실패하면 동의도 그대로이고 아무 기록도 지워지지 않는다")
    void 지우는_도중에_실패하면_아무것도_바뀌지_않는다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID hayun = family.id("하윤");
        Map<String, Long> before = leftovers.referencing(everyone(family, mom));
        Map<String, Object> profileBefore = profileRow(hayun);

        try {
            failure.failNext();
            consent(mom, hayun, false, false)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
        } finally {
            failure.reset();
        }

        assertThat(leftovers.referencing(everyone(family, mom))).isEqualTo(before);
        assertThat(profileRow(hayun)).isEqualTo(profileBefore);
        assertThat(consentKinds(hayun)).containsExactly("GRANTED");
        assertThat(profileIn(family.familyId(), hayun).get("consentGiven").asBoolean())
                .isTrue();
    }

    // ===== 도우미 =====

    ResultActions consent(Session session, UUID profileId, boolean personalData, boolean healthData) throws Exception {
        return mvc.perform(as(
                session,
                json(
                        patch("/api/v1/profiles/" + profileId + "/consent"),
                        Map.of("personalData", personalData, "healthData", healthData))));
    }

    /** 유소년 종목 두 가지로 오늘 잰다. */
    ResultActions measure(Session session, UUID profileId) throws Exception {
        return mvc.perform(as(
                session,
                post("/api/v1/profiles/" + profileId + "/fitness-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fitnessBody(today, Map.of("012", "8.0", "022", "150")))));
    }

    void feel(Session session, UUID missionId, UUID profileId) throws Exception {
        mvc.perform(as(
                        session,
                        json(
                                post("/api/v1/missions/" + missionId + "/feedback"),
                                Map.of("profileId", profileId, "feel", "GOOD"))))
                .andExpect(status().isNoContent());
    }

    Map<String, Object> missionBody(UUID participant) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "막힌 미션");
        body.put("startDate", today);
        body.put("endDate", today);
        body.put("targetMetric", "TIMER_MINUTES");
        body.put("targetValue", 2);
        body.put("participantProfileIds", List.of(participant));
        return body;
    }

    /** 만 9세 아이를 보호자 동의와 함께 더한다. */
    UUID addChild(Session session, UUID familyId, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("birthDate", today.minusYears(9).toString());
        body.put("sex", "F");
        body.put("role", "CHILD");
        body.put("guardianConsent", Map.of("personalData", true, "healthData", true));
        return uuid(read(mvc.perform(as(session, json(post("/api/v1/families/" + familyId + "/profiles"), body)))
                        .andExpect(status().isCreated()))
                .get("profileId"));
    }

    JsonNode league(Session session, UUID familyId, @Nullable YearMonth month) throws Exception {
        var request = get("/api/v1/families/" + familyId + "/league");
        if (month != null) request.param("month", month.toString());
        return read(mvc.perform(as(session, request)).andExpect(status().isOk()));
    }

    /** 이 가족이 혼자 든 지난달 방을 정산이 끝난 채로 넣는다. 다른 시험의 방과 겹치지 않게 방 번호는 무작위다. */
    void settledLastMonth(UUID familyId, YearMonth month, int rate) {
        UUID round = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.of(month.atEndOfMonth().atStartOfDay(), ZoneOffset.UTC);
        jdbc.update(
                "insert into league_rounds (id, round_month, tier, group_no, created_at, settled_at)"
                        + " values (?, ?, 'DIAMOND', ?, ?, ?)",
                round,
                month.atDay(1),
                ThreadLocalRandom.current().nextInt(1000, 30000),
                at,
                at);
        jdbc.update(
                "insert into league_members (round_id, family_id, round_month, seat_no, joined_at, final_rate,"
                        + " final_rank, moved, final_score) values (?, ?, ?, 1, ?, ?, 1, 'STAY', ?)",
                round,
                familyId,
                month.atDay(1),
                at,
                rate,
                rate / 100.0);
    }

    List<UUID> soloMissionsOf(UUID familyId, UUID profileId) {
        return idsOf("""
                select m.id from missions m
                where m.family_id = ?
                  and exists (select 1 from mission_participants p where p.mission_id = m.id and p.profile_id = ?)
                  and not exists (select 1 from mission_participants q where q.mission_id = m.id and q.profile_id <> ?)
                """, familyId, profileId, profileId);
    }

    Map<String, Object> profileRow(UUID profileId) {
        return jdbc.queryForMap(
                "select display_name, birth_date, sex, family_id, role, user_id, height_cm, weight_kg,"
                        + " consent_revoked_at from profiles where id = ?",
                profileId);
    }

    List<String> consentKinds(UUID profileId) {
        return jdbc.queryForList(
                "select kind from consent_events where profile_id = ? order by id", String.class, profileId);
    }
}
