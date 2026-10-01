package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * 탈퇴(DELETE /me)와 구성원 내보내기(DELETE /families/{familyId}/profiles/{profileId}). 실제 API 로 기록을 쌓은 뒤 지우고,
 * 지운 사람을 가리키는 행이 DB 어디에도 남지 않는지 information_schema 로 모든 표를 훑어 본다. 기록을 쌓는 도우미와 체험 가족
 * 설명은 {@link FamilyApiTestBase} 에 있다. H2 용 시험과 PostgreSQL 용 시험이 이 클래스를 상속한다.
 */
abstract class AccountDeletionApiTestBase extends FamilyApiTestBase {
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
        // 아직 아무도 쓰지 않은 가족 초대(아이 초대는 보호자 동의를 미리 받아 둔다)
        createFamilyInvite(mom, familyId, "CHILD");
        createFamilyInvite(mom, familyId, "PARENT");
        assertThat(leftovers.referencing(List.of(familyId, momId, mom.userId())))
                .containsKeys(
                        "activity_daily.profile_id",
                        "coach_messages.profile_id",
                        "exercise_favorites.profile_id",
                        "family_invites.consent_by_user_id",
                        "family_invites.family_id",
                        "family_invites.issued_by_profile_id",
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
        assertThat(count("select count(*) from family_invites where family_id = ?", familyId))
                .isZero();
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
        // 아빠가 아이 가족 초대를 냈다(보호자 동의를 미리 했다)
        String dadInvite = createFamilyInvite(dad, familyId, "CHILD");
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
                        "family_invites.consent_by_user_id",
                        "family_invites.issued_by_profile_id",
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
        // 아빠가 낸 가족 초대는 살아 있고 오너가 낸 것으로 바뀐다. 미리 한 동의는 누가 했는지만 비운다
        assertThat(jdbc.queryForObject(
                        "select issued_by_profile_id from family_invites where code = ?", UUID.class, dadInvite))
                .isEqualTo(momId);
        assertThat(jdbc.queryForObject(
                        "select consent_by_user_id from family_invites where code = ?", UUID.class, dadInvite))
                .isNull();
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

    @Test
    @DisplayName("가족 초대코드로 들어온 아이가 탈퇴하면 아이 프로필과 계정이 지워지고, 쓴 초대에서 그 계정 칸만 비운다")
    void 가족_초대로_들어온_아이가_탈퇴한다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        String code = createFamilyInvite(mom, family.familyId(), "CHILD");
        Session kid = devLogin();
        UUID kidId = joinByFamilyInvite(kid, code, "막내", today.minusYears(9).toString());
        cheer(kid, family.familyId(), kidId, family.id("엄마"), "DONE", null, null);
        personalTouches(kid, kidId);
        assertThat(leftovers.referencing(List.of(kidId, kid.userId())))
                .containsKeys(
                        "cheers.from_profile_id",
                        "consent_events.profile_id",
                        "family_invites.claimed_by_user_id",
                        "profiles.id",
                        "profiles.user_id");

        withdraw(kid).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(kidId, kid.userId()))).isEmpty();
        assertThat(familyOf(mom).ids().keySet()).containsExactly("엄마", "아빠", "하윤", "서준");
        // 쓴 초대는 남아 같은 코드를 다시 쓰지 못한다
        assertThat(count("select count(*) from family_invites where code = ? and claimed_at is not null", code))
                .isEqualTo(1);
        mvc.perform(as(devLogin(), json(post("/api/v1/profiles/claim"), joinBody(code, "또", "2015-01-01"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
        expectGoneAccount(kid);
    }

    // ===== 구성원 내보내기 =====

    @Test
    @DisplayName("오너가 가족 초대코드로 들어온 보호자를 내보내면, 그 사람이 낸 초대는 오너가 낸 것으로 바뀌고 계정은 남는다")
    void 오너가_가족_초대로_들어온_보호자를_내보낸다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        UUID familyId = family.familyId();
        String code = createFamilyInvite(mom, familyId, "PARENT");
        Session uncle = devLogin();
        UUID uncleId = joinByFamilyInvite(uncle, code, "삼촌", "1990-05-05");
        String uncleInvite = createFamilyInvite(uncle, familyId, "CHILD");
        mvc.perform(as(uncle, json(put("/api/v1/profiles/" + uncleId + "/availability"), slots())))
                .andExpect(status().isOk());
        assertThat(leftovers.referencing(List.of(uncleId)))
                .containsKeys("family_invites.issued_by_profile_id", "profile_availability_slots.profile_id");
        assertThat(leftovers.referencing(List.of(uncle.userId())))
                .containsKeys("family_invites.claimed_by_user_id", "family_invites.consent_by_user_id");

        remove(mom, familyId, uncleId).andExpect(status().isNoContent());

        assertThat(leftovers.referencing(List.of(uncleId))).isEmpty();
        // 계정은 남되 이 가족에 남긴 흔적(초대를 쓴 계정, 미리 한 동의)은 비운다
        assertThat(leftovers.referencing(List.of(uncle.userId())))
                .containsOnlyKeys("users.id", "refresh_tokens.user_id");
        assertThat(jdbc.queryForObject(
                        "select issued_by_profile_id from family_invites where code = ?", UUID.class, uncleInvite))
                .isEqualTo(family.id("엄마"));
        mvc.perform(as(uncle, get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("CREATE_FAMILY"));
        assertThat(familyOf(mom).ids().keySet()).containsExactly("엄마", "아빠", "하윤", "서준");
        // 남은 초대는 그대로 쓸 수 있고, 미리 한 동의는 누가 했는지 모르는 채로 아이 프로필에 들어간다
        Session kid = devLogin();
        UUID kidId =
                joinByFamilyInvite(kid, uncleInvite, "조카", today.minusYears(7).toString());
        assertThat(profileIn(familyId, kidId).get("consentGiven").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select consent_by_user_id from profiles where id = ?", UUID.class, kidId))
                .isNull();
    }

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
}
