package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyInFamilyException;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyThankedException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * H2(PostgreSQL 모드) + Flyway + 실제 보안 필터를 거치는 identity 엔드포인트 통합 테스트.
 * 계정은 개발용 로그인(`app.auth.dev-login.enabled=true`)으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityApiTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private TestAuth testAuth;

    @Autowired
    private ProfileQuery profileQuery;

    @Autowired
    private CheerRepository cheerRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now();

    private record Session(UUID userId, String bearer, String refreshToken, JsonNode body) {}

    private Session devLogin() throws Exception {
        return devLogin("dev-" + UUID.randomUUID(), null);
    }

    private Session devLoginWithCode(String claimCode) throws Exception {
        return devLogin("dev-" + UUID.randomUUID(), claimCode);
    }

    private Session devLogin(String providerUserId, @Nullable String claimCode) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("providerUserId", providerUserId);
        request.put("email", providerUserId + "@example.com");
        if (claimCode != null) request.put("claimCode", claimCode);
        JsonNode body = json.readTree(mvc.perform(json(post("/api/v1/auth/dev-login"), request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return new Session(
                UUID.fromString(body.get("userId").asString()),
                "Bearer " + body.get("accessToken").asString(),
                body.get("refreshToken").asString(),
                body);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, Session session) {
        return builder.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    private JsonNode createFamily(Session session) throws Exception {
        return createFamily(session, "우리 가족");
    }

    private JsonNode createFamily(Session session, String familyName) throws Exception {
        return json.readTree(mvc.perform(json(
                        auth(post("/api/v1/families"), session),
                        Map.of(
                                "familyName",
                                familyName,
                                "owner",
                                Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "F"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private ResultActions addMember(Session session, String familyId, String name, LocalDate birthDate, String role)
            throws Exception {
        return addMember(session, familyId, name, birthDate, role, null, "M");
    }

    private ResultActions addMember(
            Session session,
            String familyId,
            String name,
            LocalDate birthDate,
            String role,
            @Nullable boolean[] consent)
            throws Exception {
        return addMember(session, familyId, name, birthDate, role, consent, "M");
    }

    private ResultActions addMember(
            Session session,
            String familyId,
            String name,
            LocalDate birthDate,
            String role,
            @Nullable boolean[] consent,
            String sex)
            throws Exception {
        return postMember(session, familyId, memberBody(name, birthDate, role, consent, sex));
    }

    private Map<String, Object> memberBody(
            String name, LocalDate birthDate, String role, @Nullable boolean[] consent, String sex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("birthDate", birthDate.toString());
        body.put("sex", sex);
        body.put("role", role);
        if (consent != null) {
            body.put("guardianConsent", Map.of("personalData", consent[0], "healthData", consent[1]));
        }
        return body;
    }

    private ResultActions postMember(Session session, String familyId, Map<String, Object> body) throws Exception {
        return mvc.perform(json(auth(post("/api/v1/families/" + familyId + "/profiles"), session), body));
    }

    /** 상태 코드와 오류 봉투의 code 를 한 줄로. 예: `400 BAD_REQUEST`, 성공이면 `201`. */
    private String outcome(ResultActions action) throws Exception {
        String body = action.andReturn().getResponse().getContentAsString();
        int status = action.andReturn().getResponse().getStatus();
        JsonNode error = body.isBlank() ? null : json.readTree(body).get("error");
        return error == null
                ? String.valueOf(status)
                : status + " " + error.get("code").asString();
    }

    private Map<String, Object> without(Map<String, Object> body, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.remove(key);
        return copy;
    }

    private Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put(key, value);
        return copy;
    }

    private ResultActions invite(Session session, String profileId) throws Exception {
        return mvc.perform(auth(post("/api/v1/profiles/" + profileId + "/invite"), session));
    }

    private ResultActions claim(Session session, String code) throws Exception {
        return mvc.perform(json(auth(post("/api/v1/profiles/claim"), session), Map.of("claimCode", code)));
    }

    private ResultActions preview(Session session, String code) throws Exception {
        return mvc.perform(auth(get("/api/v1/invites/" + code), session));
    }

    private JsonNode read(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }

    private ResultActions editProfile(Session session, String profileId, Map<String, Object> body) throws Exception {
        return mvc.perform(json(auth(patch("/api/v1/profiles/" + profileId), session), body));
    }

    private ResultActions consent(Session session, String profileId, boolean personal, boolean health)
            throws Exception {
        return mvc.perform(json(
                auth(patch("/api/v1/profiles/" + profileId + "/consent"), session),
                Map.of("personalData", personal, "healthData", health)));
    }

    private String ownerProfileId(Session session) throws Exception {
        return read(mvc.perform(auth(get("/api/v1/me"), session)))
                .get("profiles")
                .get(0)
                .get("profileId")
                .asString();
    }

    private JsonNode profileIn(Session session, String familyId, String profileId) throws Exception {
        JsonNode profiles = read(mvc.perform(auth(get("/api/v1/families/" + familyId + "/profiles"), session)))
                .get("profiles");
        return StreamSupport.stream(profiles.spliterator(), false)
                .filter(it -> it.get("profileId").asString().equals(profileId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("개발용 로그인 → 가족 생성 → 아이 추가 → 목록")
    void 개발용_로그인_가족_생성_아이_추가_목록() throws Exception {
        Session parent = devLogin();
        assertThat(parent.body().get("nextStep").asString()).isEqualTo("CREATE_FAMILY");
        assertThat(parent.body().get("profiles").isEmpty()).isTrue();

        JsonNode family = createFamily(parent);
        String familyId = family.get("familyId").asString();
        assertThat(family.get("familyName").asString()).isEqualTo("우리 가족");
        JsonNode owner = family.get("ownerProfile");
        assertThat(owner.get("role").asString()).isEqualTo("PARENT");
        assertThat(owner.get("hasAccount").asBoolean()).isTrue();
        assertThat(owner.get("ageGroup").asString()).isEqualTo("성인");
        assertThat(owner.get("inviteStatus").asString()).isEqualTo("NONE");
        assertThat(owner.get("supportMode").isNull()).isTrue();
        assertThat(owner.get("measurable").asBoolean()).isTrue();
        assertThat(owner.get("consentRequired").asBoolean()).isFalse();
        assertThat(owner.get("consentGiven").asBoolean()).isTrue();
        assertThat(owner.get("familyId").asString()).isEqualTo(familyId);

        mvc.perform(json(
                        auth(post("/api/v1/families"), parent),
                        Map.of(
                                "familyName",
                                "또",
                                "owner",
                                Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "F"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_IN_FAMILY"));

        LocalDate childBirth = today.minusYears(8);
        addMember(parent, familyId, "첫째", childBirth, "CHILD")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        addMember(parent, familyId, "첫째", childBirth, "CHILD", new boolean[] {true, false})
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));

        JsonNode child = read(addMember(parent, familyId, "첫째", childBirth, "CHILD", new boolean[] {true, true})
                .andExpect(status().isCreated()));
        assertThat(child.get("profileId").asString()).isNotBlank();
        assertThat(child.get("name").asString()).isEqualTo("첫째");
        assertThat(child.get("role").asString()).isEqualTo("CHILD");
        assertThat(child.get("ageGroup").asString()).isEqualTo("유소년");
        assertThat(child.get("hasAccount").asBoolean()).isFalse();
        assertThat(child.get("measurable").asBoolean()).isTrue();
        assertThat(child.get("consentRequired").asBoolean()).isTrue();
        assertThat(child.get("consentGiven").asBoolean()).isTrue();

        JsonNode toddler =
                read(addMember(parent, familyId, "막내", today.minusYears(2), "CHILD", new boolean[] {true, true})
                        .andExpect(status().isCreated()));
        assertThat(toddler.get("ageGroup").asString()).isEqualTo("유아기");
        assertThat(toddler.get("measurable").asBoolean()).isFalse();

        JsonNode listed = read(mvc.perform(auth(get("/api/v1/families/" + familyId + "/profiles"), parent))
                .andExpect(status().isOk()));
        assertThat(listed.get("familyId").asString()).isEqualTo(familyId);
        assertThat(listed.get("familyName").asString()).isEqualTo("우리 가족");
        assertThat(listed.get("profiles").size()).isEqualTo(3);

        Session stranger = devLogin();
        mvc.perform(auth(get("/api/v1/families/" + familyId + "/profiles"), stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        addMember(stranger, familyId, "침입", childBirth, "CHILD", new boolean[] {true, true})
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        mvc.perform(auth(get("/api/v1/families/" + UUID.randomUUID() + "/profiles"), parent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_FOUND"));

        JsonNode me = read(mvc.perform(auth(get("/api/v1/me"), parent)).andExpect(status().isOk()));
        assertThat(me.get("userId").asString()).isEqualTo(parent.userId().toString());
        assertThat(me.get("nextStep").asString()).isEqualTo("HOME");
        assertThat(me.get("profiles").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("초대 → 두 번째 계정이 코드 사용 → 참여 수준 → 동의 철회")
    void 초대_두_번째_계정이_코드_사용_참여_수준_동의_철회() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        String ownerId = read(mvc.perform(auth(get("/api/v1/me"), parent)))
                .get("profiles")
                .get(0)
                .get("profileId")
                .asString();
        String childId = read(addMember(
                        parent, familyId, "첫째", today.minusYears(10), "CHILD", new boolean[] {true, true}))
                .get("profileId")
                .asString();
        String dadId = read(addMember(parent, familyId, "아빠", today.minusYears(40), "PARENT"))
                .get("profileId")
                .asString();

        invite(parent, ownerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
        invite(devLogin(), childId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));

        JsonNode invitation = read(invite(parent, childId).andExpect(status().isCreated()));
        String code = invitation.get("claimCode").asString();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
        assertThat(invitation.get("expiresAt").asString()).isNotBlank();
        assertThat(invitation.get("shareUrl").asString()).isEqualTo("http://localhost:5173/claim?code=" + code);
        JsonNode issued = read(mvc.perform(auth(get("/api/v1/families/" + familyId + "/profiles"), parent)))
                .get("profiles");
        assertThat(StreamSupport.stream(issued.spliterator(), false)
                        .filter(it -> it.get("profileId").asString().equals(childId))
                        .findFirst()
                        .orElseThrow()
                        .get("inviteStatus")
                        .asString())
                .isEqualTo("ISSUED");

        Session childUser = devLoginWithCode(code);
        assertThat(childUser.body().get("nextStep").asString()).isEqualTo("CLAIM");

        claim(childUser, "zzzzzz")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CODE_NOT_FOUND"));
        claim(parent, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));

        JsonNode claimed = read(claim(childUser, code.toLowerCase()).andExpect(status().isOk()));
        assertThat(claimed.get("profileId").asString()).isEqualTo(childId);
        assertThat(claimed.get("familyId").asString()).isEqualTo(familyId);
        assertThat(claimed.get("role").asString()).isEqualTo("CHILD");
        assertThat(claimed.get("nextStep").asString()).isEqualTo("HOME");

        claim(devLogin(), code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
        invite(parent, childId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));

        JsonNode childMe = read(mvc.perform(auth(get("/api/v1/me"), childUser)).andExpect(status().isOk()));
        assertThat(childMe.get("nextStep").asString()).isEqualTo("HOME");
        assertThat(childMe.get("profiles").get(0).get("profileId").asString()).isEqualTo(childId);
        assertThat(childMe.get("profiles").get(0).get("hasAccount").asBoolean()).isTrue();
        assertThat(childMe.get("profiles").get(0).get("inviteStatus").asString())
                .isEqualTo("CLAIMED");

        String dadCode = read(invite(parent, dadId).andExpect(status().isCreated()))
                .get("claimCode")
                .asString();
        Session dadUser = devLogin();
        assertThat(read(claim(dadUser, dadCode).andExpect(status().isOk()))
                        .get("nextStep")
                        .asString())
                .isEqualTo("SUPPORT_MODE");
        // 참여 방식을 고르기 전에 닫고 다시 열면 /me 도 SUPPORT_MODE 다(QA CT-08). 가족을 만든 엄마는 그대로 HOME
        assertThat(read(mvc.perform(auth(get("/api/v1/me"), dadUser)))
                        .get("nextStep")
                        .asString())
                .isEqualTo("SUPPORT_MODE");
        assertThat(read(mvc.perform(auth(get("/api/v1/me"), parent)))
                        .get("nextStep")
                        .asString())
                .isEqualTo("HOME");

        // 참여 수준: 본인 PARENT 프로필만
        JsonNode changed = read(mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + dadId + "/support-mode"), dadUser),
                        Map.of("supportMode", "WEEKEND")))
                .andExpect(status().isOk()));
        assertThat(changed.get("supportMode").asString()).isEqualTo("WEEKEND");
        assertThat(changed.get("profileId").asString()).isEqualTo(dadId);
        assertThat(read(mvc.perform(auth(get("/api/v1/me"), dadUser)))
                        .get("nextStep")
                        .asString())
                .isEqualTo("HOME");
        mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + childId + "/support-mode"), childUser),
                        Map.of("supportMode", "FULL")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("NOT_APPLICABLE"));
        mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + dadId + "/support-mode"), parent),
                        Map.of("supportMode", "FULL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + ownerId + "/support-mode"), parent),
                        Map.of("supportMode", "NOPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // 동의 철회·재동의: 가족의 PARENT 만
        mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + childId + "/consent"), childUser),
                        Map.of("personalData", true, "healthData", true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        JsonNode revoked = read(mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + childId + "/consent"), parent),
                        Map.of("personalData", true, "healthData", false)))
                .andExpect(status().isOk()));
        assertThat(revoked.get("consentGiven").asBoolean()).isFalse();
        assertThat(revoked.get("consentAt").isNull()).isTrue();
        assertThat(revoked.get("consentBy").isNull()).isTrue();
        assertThat(revoked.get("measurable").asBoolean()).isFalse();
        JsonNode afterRevoke = read(mvc.perform(auth(get("/api/v1/me"), childUser)))
                .get("profiles")
                .get(0);
        assertThat(afterRevoke.get("consentGiven").asBoolean()).isFalse();
        assertThat(afterRevoke.get("measurable").asBoolean()).isFalse();

        JsonNode regranted = read(mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + childId + "/consent"), dadUser),
                        Map.of("personalData", true, "healthData", true)))
                .andExpect(status().isOk()));
        assertThat(regranted.get("consentGiven").asBoolean()).isTrue();
        assertThat(regranted.get("consentAt").asString()).isNotBlank();
        assertThat(regranted.get("consentBy").asString())
                .isEqualTo(dadUser.userId().toString());
        assertThat(regranted.get("measurable").asBoolean()).isTrue();
        mvc.perform(json(auth(patch("/api/v1/profiles/" + childId + "/consent"), parent), Map.of("personalData", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // 보호자 동의는 아이에게만 있다 — 아빠가 엄마(PARENT)의 동의를 거두지 못하고, 엄마는 막히지 않는다(QA KP-01)
        mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + ownerId + "/consent"), dadUser),
                        Map.of("personalData", false, "healthData", false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_NOT_APPLICABLE"));
        JsonNode momAfter = read(mvc.perform(auth(get("/api/v1/me"), parent)))
                .get("profiles")
                .get(0);
        assertThat(momAfter.get("consentRequired").asBoolean()).isFalse();
        assertThat(momAfter.get("consentGiven").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from consent_events where profile_id = ?",
                        Integer.class,
                        UUID.fromString(ownerId)))
                .isZero();
    }

    @Test
    @DisplayName("응원 규칙과 과다 호출")
    void 응원_규칙과_과다_호출() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        String ownerId = read(mvc.perform(auth(get("/api/v1/me"), parent)))
                .get("profiles")
                .get(0)
                .get("profileId")
                .asString();
        String childId = read(addMember(
                        parent, familyId, "첫째", today.minusYears(10), "CHILD", new boolean[] {true, true}))
                .get("profileId")
                .asString();
        String dadId = read(addMember(parent, familyId, "아빠", today.minusYears(40), "PARENT"))
                .get("profileId")
                .asString();

        JsonNode created = read(cheer(
                        parent,
                        familyId,
                        Map.of("fromProfileId", ownerId, "toProfileId", childId, "message", "힘내!", "emoji", "💪"))
                .andExpect(status().isCreated()));
        assertThat(created.get("cheerId").asString()).isNotBlank();
        assertThat(created.get("fromProfileId").asString()).isEqualTo(ownerId);
        assertThat(created.get("toProfileId").asString()).isEqualTo(childId);
        assertThat(created.get("message").asString()).isEqualTo("힘내!");
        // 옛 emoji 칸은 stickerId 로 읽고, 응답에는 두 칸에 같은 값을 싣는다
        assertThat(created.get("stickerId").asString()).isEqualTo("💪");
        assertThat(created.get("emoji").asString()).isEqualTo("💪");
        assertThat(created.get("kind").asString()).isEqualTo("PRAISE");
        assertThat(created.get("missionId").isNull()).isTrue();
        assertThat(created.get("createdAt").asString()).isNotBlank();

        cheer(parent, familyId, Map.of("fromProfileId", ownerId, "toProfileId", ownerId, "message", "나"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("SELF_CHEER"));
        cheer(
                        parent,
                        familyId,
                        Map.of(
                                "fromProfileId",
                                ownerId,
                                "toProfileId",
                                UUID.randomUUID().toString(),
                                "message",
                                "?"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("NOT_FAMILY_MEMBER"));
        // 보호자 계정은 계정 없는 아이 이름으로 보낼 수 있다(아이가 부모 기기를 빌려 쓴다).
        JsonNode asChild = read(
                cheer(parent, familyId, Map.of("fromProfileId", childId, "toProfileId", ownerId, "message", "고마워요"))
                        .andExpect(status().isCreated()));
        assertThat(asChild.get("fromProfileId").asString()).isEqualTo(childId);
        assertThat(asChild.get("toProfileId").asString()).isEqualTo(ownerId);
        assertThat(asChild.get("kind").asString()).isEqualTo("DONE");
        // 계정 없는 부모 자리(초대 전 아빠) 이름으로는 못 보낸다.
        cheer(parent, familyId, Map.of("fromProfileId", dadId, "toProfileId", childId, "message", "?"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        cheer(devLogin(), familyId, Map.of("fromProfileId", ownerId, "toProfileId", childId, "message", "?"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        cheer(parent, familyId, Map.of("fromProfileId", ownerId, "toProfileId", childId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        cheer(parent, familyId, Map.of("fromProfileId", ownerId, "toProfileId", childId, "message", "가".repeat(101)))
                .andExpect(status().isBadRequest());

        for (int i = 0; i < 4; i++) {
            cheer(parent, familyId, Map.of("fromProfileId", ownerId, "toProfileId", childId, "emoji", "👍"))
                    .andExpect(status().isCreated());
        }
        cheer(parent, familyId, Map.of("fromProfileId", ownerId, "toProfileId", childId, "emoji", "👍"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY"));
    }

    @Test
    @DisplayName("응원 종류 · 고마워요 한 번 · 미션 검사 · 받은 응원 목록")
    void 응원_종류_고마워요_한_번_미션_검사_받은_응원_목록() throws Exception {
        Session parent = devLogin();
        JsonNode family = createFamily(parent);
        String familyId = family.get("familyId").asString();
        String ownerId = family.get("ownerProfile").get("profileId").asString();
        String childId = read(addMember(
                        parent, familyId, "첫째", today.minusYears(10), "CHILD", new boolean[] {true, true}))
                .get("profileId")
                .asString();
        String missionId = createMission(parent, familyId, childId);

        // 지금 FE 모양(kind 없음): 아이 → 부모, 스티커 없음 → DONE
        String doneId = read(cheer(
                                parent,
                                familyId,
                                Map.of(
                                        "fromProfileId", childId,
                                        "toProfileId", ownerId,
                                        "message", "운동 다 했어요!",
                                        "missionId", missionId))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.kind").value("DONE")))
                .get("cheerId")
                .asString();
        // stickerId 와 옛 emoji 가 같이 오면 stickerId
        String praiseId = read(cheer(
                                parent,
                                familyId,
                                Map.of(
                                        "fromProfileId",
                                        ownerId,
                                        "toProfileId",
                                        childId,
                                        "kind",
                                        "PRAISE",
                                        "stickerId",
                                        "star",
                                        "emoji",
                                        "flag",
                                        "missionId",
                                        missionId))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.stickerId").value("star"))
                        .andExpect(jsonPath("$.emoji").value("star")))
                .get("cheerId")
                .asString();

        cheer(parent, familyId, thanks(childId, ownerId, "PRAISE", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        cheer(parent, familyId, Map.of("fromProfileId", ownerId, "toProfileId", childId, "message", "?", "kind", "HUG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        cheer(parent, familyId, thanks(childId, ownerId, "THANKS", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        cheer(parent, familyId, thanks(childId, ownerId, "THANKS", doneId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("NOT_A_REPLY_TARGET"));
        cheer(
                        parent,
                        familyId,
                        thanks(childId, ownerId, "THANKS", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHEER_NOT_FOUND"));
        String thanksId = read(cheer(parent, familyId, thanks(childId, ownerId, "THANKS", praiseId))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.kind").value("THANKS"))
                        .andExpect(jsonPath("$.replyToCheerId").value(praiseId)))
                .get("cheerId")
                .asString();
        cheer(parent, familyId, thanks(childId, ownerId, "THANKS", praiseId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_THANKED"));

        // missionId 는 이 가족의 미션이어야 한다 — 다른 가족 미션 · 없는 미션 모두 404
        Session other = devLogin();
        String otherFamily = createFamily(other).get("familyId").asString();
        String otherChild = read(addMember(
                        other, otherFamily, "남의 집 아이", today.minusYears(9), "CHILD", new boolean[] {true, true}))
                .get("profileId")
                .asString();
        for (String bad : List.of(
                createMission(other, otherFamily, otherChild), UUID.randomUUID().toString())) {
            cheer(
                            parent,
                            familyId,
                            Map.of(
                                    "fromProfileId",
                                    ownerId,
                                    "toProfileId",
                                    childId,
                                    "stickerId",
                                    "star",
                                    "missionId",
                                    bad))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("MISSION_NOT_FOUND"));
        }

        JsonNode all =
                read(cheers(parent, familyId, "").andExpect(status().isOk())).get("cheers");
        assertThat(cheerIds(all)).containsExactly(thanksId, praiseId, doneId);
        JsonNode latest = all.get(0);
        assertThat(latest.get("fromProfileId").asString()).isEqualTo(childId);
        assertThat(latest.get("fromName").asString()).isEqualTo("첫째");
        assertThat(latest.get("toProfileId").asString()).isEqualTo(ownerId);
        assertThat(latest.get("kind").asString()).isEqualTo("THANKS");
        assertThat(latest.get("stickerId").asString()).isEqualTo("heart");
        assertThat(latest.get("replyToCheerId").asString()).isEqualTo(praiseId);
        assertThat(latest.get("missionId").isNull()).isTrue();
        assertThat(latest.get("createdAt").asString()).isNotBlank();
        assertThat(cheerIds(read(cheers(parent, familyId, "?toProfileId=" + childId))
                        .get("cheers")))
                .containsExactly(praiseId);
        assertThat(cheerIds(read(cheers(parent, familyId, "?fromProfileId=" + childId))
                        .get("cheers")))
                .containsExactly(thanksId, doneId);
        assertThat(cheerIds(read(cheers(parent, familyId, "?missionId=" + missionId))
                        .get("cheers")))
                .containsExactly(praiseId, doneId);
        assertThat(cheerIds(read(cheers(parent, familyId, "?size=1")).get("cheers")))
                .containsExactly(thanksId);
        for (String badQuery : List.of("?size=0", "?size=101", "?toProfileId=abc")) {
            cheers(parent, familyId, badQuery)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }
        cheers(other, familyId, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
    }

    @Test
    @DisplayName("사전 검사를 함께 지나친 두 번째 고마워요는 유니크 인덱스가 막고 ALREADY_THANKED 로 바뀐다")
    void 사전_검사를_함께_지나친_두_번째_고마워요는_유니크_인덱스가_막는다() throws Exception {
        Session parent = devLogin();
        JsonNode family = createFamily(parent);
        String familyId = family.get("familyId").asString();
        UUID ownerId =
                UUID.fromString(family.get("ownerProfile").get("profileId").asString());
        UUID childId = UUID.fromString(
                read(addMember(parent, familyId, "첫째", today.minusYears(10), "CHILD", new boolean[] {true, true}))
                        .get("profileId")
                        .asString());
        UUID praiseId = UUID.fromString(read(cheer(
                        parent,
                        familyId,
                        Map.of(
                                "fromProfileId", ownerId.toString(),
                                "toProfileId", childId.toString(),
                                "stickerId", "star")))
                .get("cheerId")
                .asString());
        // 서비스의 existsReplyTo 검사를 건너뛰고 저장소에 바로 두 번 넣는다(두 요청이 검사를 함께 지나친 경우)
        tx.executeWithoutResult(status -> cheerRepository.save(thanksFor(familyId, childId, ownerId, praiseId)));

        assertThatThrownBy(() -> tx.executeWithoutResult(
                        status -> cheerRepository.save(thanksFor(familyId, childId, ownerId, praiseId))))
                .isInstanceOf(AlreadyThankedException.class);
    }

    private Cheer thanksFor(String familyId, UUID from, UUID to, UUID replyToCheerId) {
        return new Cheer(
                UUID.randomUUID(),
                UUID.fromString(familyId),
                from,
                to,
                CheerKind.THANKS,
                null,
                "heart",
                null,
                replyToCheerId,
                Instant.now());
    }

    /** 고마워요 요청 본문. replyToCheerId 는 있을 때만 싣는다. */
    private static Map<String, Object> thanks(
            String fromProfileId, String toProfileId, String kind, @Nullable String replyToCheerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromProfileId", fromProfileId);
        body.put("toProfileId", toProfileId);
        body.put("message", "고마워요 · 사랑해");
        body.put("stickerId", "heart");
        body.put("kind", kind);
        if (replyToCheerId != null) body.put("replyToCheerId", replyToCheerId);
        return body;
    }

    /** 내일 하루짜리 걸음수 미션을 만든다. */
    private String createMission(Session session, String familyId, String participantId) throws Exception {
        String day = today.plusDays(1).toString();
        Map<String, Object> body = Map.of(
                "title",
                "걷기",
                "startDate",
                day,
                "endDate",
                day,
                "targetMetric",
                "STEPS",
                "targetValue",
                3000,
                "participantProfileIds",
                List.of(participantId));
        return read(mvc.perform(json(auth(post("/api/v1/families/" + familyId + "/missions"), session), body))
                        .andExpect(status().isCreated()))
                .get("missionId")
                .asString();
    }

    private ResultActions cheers(Session session, String familyId, String query) throws Exception {
        return mvc.perform(auth(get("/api/v1/families/" + familyId + "/cheers" + query), session));
    }

    private static List<String> cheerIds(JsonNode cheers) {
        return StreamSupport.stream(cheers.spliterator(), false)
                .map(it -> it.get("cheerId").asString())
                .toList();
    }

    private ResultActions cheer(Session session, String familyId, Map<String, Object> body) throws Exception {
        return mvc.perform(json(auth(post("/api/v1/families/" + familyId + "/cheers"), session), body));
    }

    @Test
    @DisplayName("토큰 없는 요청은 401 이고 리프레시는 리프레시 토큰만 받는다")
    void 토큰_없는_요청은_401_이고_리프레시는_리프레시_토큰만_받는다() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        Session parent = devLogin();
        createFamily(parent);

        JsonNode refreshed =
                read(mvc.perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", parent.refreshToken())))
                        .andExpect(status().isOk()));
        assertThat(refreshed.get("userId").asString()).isEqualTo(parent.userId().toString());
        assertThat(refreshed.get("nextStep").asString()).isEqualTo("HOME");
        assertThat(refreshed.get("profiles").size()).isEqualTo(1);
        mvc.perform(get("/api/v1/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + refreshed.get("accessToken").asString()))
                .andExpect(status().isOk());

        mvc.perform(json(
                        post("/api/v1/auth/refresh"),
                        Map.of("refreshToken", parent.bearer().substring("Bearer ".length()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + parent.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // TestAuth 가 만든 토큰도 같은 경로로 통한다.
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, testAuth.bearer(parent.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(parent.userId().toString()));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", refreshToken)));
    }

    private ResultActions logout(Object body) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/logout"), body));
    }

    @Test
    @DisplayName("리프레시 토큰은 한 번만 쓴다 — 다시 쓰면 그 로그인의 토큰이 모두 끊기고 401 INVALID_REFRESH_TOKEN")
    void 리프레시_토큰은_한_번만_쓴다() throws Exception {
        Session parent = devLogin();

        String second = read(refresh(parent.refreshToken()).andExpect(status().isOk()))
                .get("refreshToken")
                .asString();
        assertThat(second).isNotEqualTo(parent.refreshToken());

        refresh(parent.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        // 401 을 내면서도 묶음 폐기는 커밋됐다 — 회전으로 받은 새 토큰도 끊긴다.
        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        // 액세스 토큰은 상태 없는 JWT 라 만료까지는 그대로 통한다.
        mvc.perform(auth(get("/api/v1/me"), parent)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃은 그 로그인의 리프레시 토큰만 끊고 204 다 — 본문이 없거나 모르는 토큰이어도 204")
    void 로그아웃은_그_로그인의_리프레시_토큰만_끊고_204_다() throws Exception {
        String providerUserId = "dev-" + UUID.randomUUID();
        Session phone = devLogin(providerUserId, null);
        Session tablet = devLogin(providerUserId, null);

        logout(Map.of("refreshToken", phone.refreshToken())).andExpect(status().isNoContent());

        refresh(phone.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        refresh(tablet.refreshToken()).andExpect(status().isOk());

        logout(Map.of("refreshToken", phone.refreshToken())).andExpect(status().isNoContent());
        logout(Map.of("refreshToken", "not-a-token")).andExpect(status().isNoContent());
        logout(Map.of()).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("필수 누락과 형식 오류는 400 BAD_REQUEST")
    void 필수_누락과_형식_오류는_400_BAD_REQUEST() throws Exception {
        mvc.perform(json(post("/api/v1/auth/dev-login"), Map.of("email", "x@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(json(post("/api/v1/auth/google"), Map.of("authorizationCode", "", "redirectUri", "https://app")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        Session parent = devLogin();
        mvc.perform(json(
                        auth(post("/api/v1/families"), parent),
                        Map.of("familyName", "", "owner", Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "F"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(json(
                        auth(post("/api/v1/families"), parent),
                        Map.of(
                                "familyName",
                                "가",
                                "owner",
                                Map.of("name", "엄마", "birthDate", "2999-01-01", "sex", "F"))))
                .andExpect(status().isBadRequest());
        mvc.perform(json(
                        auth(post("/api/v1/families"), parent),
                        Map.of(
                                "familyName",
                                "가",
                                "owner",
                                Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "X"))))
                .andExpect(status().isBadRequest());
        mvc.perform(json(auth(post("/api/v1/families"), parent), "{not json")).andExpect(status().isBadRequest());
        mvc.perform(json(auth(post("/api/v1/profiles/claim"), parent), Map.of("claimCode", " ")))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/v1/profiles/not-a-uuid/invite"), parent)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("필수 칸이 빠지면 500 이 아니라 400 BAD_REQUEST 다 — 가족 만들기 · 구성원 추가 · 참여 수준 · 응원")
    void 필수_칸이_빠지면_500_이_아니라_400_BAD_REQUEST_다() throws Exception {
        Session parent = devLogin();
        Map<String, Object> owner = Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "F");
        Map<String, String> outcomes = new LinkedHashMap<>();
        outcomes.put(
                "가족 만들기 · owner 없음",
                outcome(mvc.perform(json(auth(post("/api/v1/families"), parent), Map.of("familyName", "가")))));
        for (String key : List.of("birthDate", "sex")) {
            outcomes.put(
                    "가족 만들기 · owner." + key + " 없음",
                    outcome(mvc.perform(json(
                            auth(post("/api/v1/families"), parent),
                            Map.of("familyName", "가", "owner", without(owner, key))))));
        }

        String familyId = createFamily(parent).get("familyId").asString();
        String ownerId = read(mvc.perform(auth(get("/api/v1/me"), parent)))
                .get("profiles")
                .get(0)
                .get("profileId")
                .asString();
        Map<String, Object> member = memberBody("첫째", today.minusYears(8), "CHILD", new boolean[] {true, true}, "M");
        String childId = read(postMember(parent, familyId, member).andExpect(status().isCreated()))
                .get("profileId")
                .asString();
        for (String key : List.of("birthDate", "sex", "role")) {
            outcomes.put("구성원 추가 · " + key + " 없음", outcome(postMember(parent, familyId, without(member, key))));
        }

        Map<String, Object> nullMode = new HashMap<>();
        nullMode.put("supportMode", null);
        outcomes.put(
                "참여 수준 · supportMode 없음",
                outcome(mvc.perform(json(auth(patch("/api/v1/profiles/" + ownerId + "/support-mode"), parent), "{}"))));
        outcomes.put(
                "참여 수준 · supportMode null",
                outcome(mvc.perform(
                        json(auth(patch("/api/v1/profiles/" + ownerId + "/support-mode"), parent), nullMode))));
        outcomes.put(
                "응원 · fromProfileId 없음",
                outcome(cheer(parent, familyId, Map.of("toProfileId", childId, "message", "힘내"))));
        outcomes.put(
                "응원 · toProfileId 없음",
                outcome(cheer(parent, familyId, Map.of("fromProfileId", ownerId, "message", "힘내"))));

        Map<String, String> expected = new LinkedHashMap<>();
        outcomes.keySet().forEach(key -> expected.put(key, "400 BAD_REQUEST"));
        assertThat(outcomes).isEqualTo(expected);
    }

    @Test
    @DisplayName("프로필 요약에 성별(M · F)이 실린다 — 가족 만들기 · 구성원 추가 · 구성원 목록 · /me")
    void 프로필_요약에_성별이_실린다() throws Exception {
        Session parent = devLogin();
        JsonNode family = createFamily(parent);
        String familyId = family.get("familyId").asString();
        JsonNode child =
                read(addMember(parent, familyId, "첫째", today.minusYears(8), "CHILD", new boolean[] {true, true}, "M")
                        .andExpect(status().isCreated()));
        JsonNode listed = read(mvc.perform(auth(get("/api/v1/families/" + familyId + "/profiles"), parent))
                        .andExpect(status().isOk()))
                .get("profiles");
        JsonNode me = read(mvc.perform(auth(get("/api/v1/me"), parent)).andExpect(status().isOk()));

        Map<String, String> sexes = new LinkedHashMap<>();
        sexes.put("가족 만들기 ownerProfile", sexOf(family.get("ownerProfile")));
        sexes.put("구성원 추가 응답", sexOf(child));
        listed.forEach(it -> sexes.put("구성원 목록 " + it.get("name").asString(), sexOf(it)));
        sexes.put("/me profiles[0]", sexOf(me.get("profiles").get(0)));

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("가족 만들기 ownerProfile", "F");
        expected.put("구성원 추가 응답", "M");
        expected.put("구성원 목록 엄마", "F");
        expected.put("구성원 목록 첫째", "M");
        expected.put("/me profiles[0]", "F");
        assertThat(sexes).isEqualTo(expected);
    }

    private static String sexOf(JsonNode profile) {
        JsonNode sex = profile.get("sex");
        return sex == null ? "(칸 없음)" : sex.asString();
    }

    @Test
    @DisplayName("다시 로그인하거나 리프레시한 응답의 userId · nextStep · profiles 는 /me 와 같다")
    void 다시_로그인하거나_리프레시한_응답의_userId_nextStep_profiles_는_me_와_같다() throws Exception {
        String providerUserId = "dev-" + UUID.randomUUID();
        Session parent = devLogin(providerUserId, null);
        createFamily(parent);

        JsonNode me = read(mvc.perform(auth(get("/api/v1/me"), parent)).andExpect(status().isOk()));
        JsonNode login = devLogin(providerUserId, null).body();
        JsonNode refreshed =
                read(mvc.perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", parent.refreshToken())))
                        .andExpect(status().isOk()));

        assertThat(me.get("nextStep").asString()).isEqualTo("HOME");
        assertThat(me.get("profiles").size()).isEqualTo(1);
        assertThat(me.get("selfProfileId").asString())
                .isEqualTo(me.get("profiles").get(0).get("profileId").asString());
        for (JsonNode session : List.of(login, refreshed)) {
            assertThat(session.get("userId")).isEqualTo(me.get("userId"));
            assertThat(session.get("nextStep")).isEqualTo(me.get("nextStep"));
            assertThat(session.get("profiles")).isEqualTo(me.get("profiles"));
            assertThat(session.get("selfProfileId")).isEqualTo(me.get("selfProfileId"));
        }
    }

    @Test
    @DisplayName("selfProfileId 는 이 계정의 프로필이고, 가족이 없으면 null 이다")
    void selfProfileId_는_이_계정의_프로필이고_가족이_없으면_null_이다() throws Exception {
        Session fresh = devLogin();
        assertThat(fresh.body().has("selfProfileId")).isTrue();
        assertThat(fresh.body().get("selfProfileId").isNull()).isTrue();
        assertThat(read(mvc.perform(auth(get("/api/v1/me"), fresh)))
                        .get("selfProfileId")
                        .isNull())
                .isTrue();

        JsonNode family = createFamily(fresh);
        String ownerId = family.get("ownerProfile").get("profileId").asString();
        addMember(fresh, family.get("familyId").asString(), "첫째", today.minusYears(10), "CHILD", new boolean[] {
                    true, true
                })
                .andExpect(status().isCreated());

        JsonNode me = read(mvc.perform(auth(get("/api/v1/me"), fresh)).andExpect(status().isOk()));
        assertThat(me.get("selfProfileId").asString()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("초대코드 미리 보기 — 로그인 필요 · 자리와 보낸 보호자 · 없음 404 · 이미 사용 409")
    void 초대코드_미리_보기() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent, "서준이네").get("familyId").asString();
        String childId = read(addMember(
                        parent, familyId, "서준", today.minusYears(10), "CHILD", new boolean[] {true, true}))
                .get("profileId")
                .asString();
        JsonNode invitation = read(invite(parent, childId).andExpect(status().isCreated()));
        String code = invitation.get("claimCode").asString();

        // 살아 있는 코드가 있으면 다시 눌러도 같은 코드 · 같은 만료다
        JsonNode again = read(invite(parent, childId).andExpect(status().isCreated()));
        assertThat(again.get("claimCode").asString()).isEqualTo(code);
        // DB 는 마이크로초까지 두므로 처음 응답(메모리 값)과는 1ms 안에서 같다
        assertThat(Instant.parse(again.get("expiresAt").asString()))
                .isCloseTo(Instant.parse(invitation.get("expiresAt").asString()), within(1, ChronoUnit.MILLIS));

        mvc.perform(get("/api/v1/invites/" + code))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        Session newcomer = devLoginWithCode(code);
        JsonNode seat = read(preview(newcomer, code.toLowerCase()).andExpect(status().isOk()));
        assertThat(seat.get("familyName").asString()).isEqualTo("서준이네");
        assertThat(seat.get("profileName").asString()).isEqualTo("서준");
        assertThat(seat.get("role").asString()).isEqualTo("CHILD");
        assertThat(seat.get("ageGroup").asString()).isEqualTo("유소년");
        assertThat(seat.get("invitedByName").asString()).isEqualTo("엄마");
        assertThat(Instant.parse(seat.get("expiresAt").asString()))
                .isCloseTo(Instant.parse(invitation.get("expiresAt").asString()), within(1, ChronoUnit.MILLIS));
        assertThat(seat.has("profileId")).isFalse();
        assertThat(seat.has("familyId")).isFalse();

        preview(newcomer, "ZZZZZZ")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CODE_NOT_FOUND"));
        preview(newcomer, "not-a-code")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CODE_NOT_FOUND"));

        claim(newcomer, code).andExpect(status().isOk());
        preview(devLogin(), code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
    }

    @Test
    @DisplayName("없는 코드를 10번 넣은 계정은 미리 보기 · 수락 모두 429 TOO_MANY — 맞는 코드여도")
    void 없는_코드를_10번_넣은_계정은_429_TOO_MANY() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        String dadId = read(addMember(parent, familyId, "아빠", today.minusYears(40), "PARENT"))
                .get("profileId")
                .asString();
        String code = read(invite(parent, dadId)).get("claimCode").asString();
        Session guesser = devLogin();

        for (int i = 0; i < 5; i++) {
            preview(guesser, "ZZZZZZ").andExpect(status().isNotFound());
            claim(guesser, "ZZZZZ2").andExpect(status().isNotFound());
        }

        preview(guesser, code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY"));
        claim(guesser, code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY"));
        // 다른 계정은 그대로 넣을 수 있다
        assertThat(read(claim(devLogin(), code).andExpect(status().isOk()))
                        .get("profileId")
                        .asString())
                .isEqualTo(dadId);
    }

    @Test
    @DisplayName("한 계정 한 가족 — 다른 가족이 있는 계정의 초대 수락은 409 ALREADY_IN_FAMILY 이고 자리는 그대로 남는다")
    void 다른_가족이_있는_계정의_초대_수락은_409_ALREADY_IN_FAMILY() throws Exception {
        Session mom = devLogin();
        String familyId = createFamily(mom, "서준이네").get("familyId").asString();
        String dadSeat = read(addMember(mom, familyId, "아빠", today.minusYears(40), "PARENT"))
                .get("profileId")
                .asString();
        String code = read(invite(mom, dadSeat)).get("claimCode").asString();

        Session dad = devLogin();
        createFamily(dad, "아빠네");

        // 미리 보기는 구성원 검사를 하지 않는다
        preview(dad, code).andExpect(status().isOk());
        claim(dad, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_IN_FAMILY"));
        claim(mom, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));

        JsonNode seats = read(mvc.perform(auth(get("/api/v1/families/" + familyId + "/profiles"), mom)))
                .get("profiles");
        JsonNode seat = StreamSupport.stream(seats.spliterator(), false)
                .filter(it -> it.get("profileId").asString().equals(dadSeat))
                .findFirst()
                .orElseThrow();
        assertThat(seat.get("hasAccount").asBoolean()).isFalse();
        assertThat(seat.get("inviteStatus").asString()).isEqualTo("ISSUED");
        JsonNode dadMe = read(mvc.perform(auth(get("/api/v1/me"), dad)));
        assertThat(dadMe.get("profiles").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("사전 검사를 함께 지나친 가족 만들기 · 초대 수락은 profiles.user_id 유니크 인덱스가 막고 ALREADY_IN_FAMILY 로 바뀐다")
    void 사전_검사를_함께_지나친_가족_만들기와_초대_수락은_유니크_인덱스가_막는다() throws Exception {
        Session mom = devLogin();
        String familyId = createFamily(mom).get("familyId").asString();
        UUID dadSeat = UUID.fromString(read(addMember(mom, familyId, "아빠", today.minusYears(40), "PARENT"))
                .get("profileId")
                .asString());
        Session dad = devLogin();
        createFamily(dad, "아빠네");

        // 서비스의 profilesOfUser 검사를 건너뛰고 저장소에 바로 넣는다(두 요청이 검사를 함께 지나친 경우)
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> familyRepository.save(
                        Family.createWithParent(dad.userId(), "또", "아빠", LocalDate.of(1986, 1, 1), Sex.M, today))))
                .isInstanceOf(AlreadyInFamilyException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(
                        status -> familyRepository.attachUserIfUnclaimed(dadSeat, dad.userId(), Instant.now())))
                .isInstanceOf(AlreadyInFamilyException.class);

        // 계정 없는 프로필(user_id null)은 여럿이어도 된다
        addMember(mom, familyId, "첫째", today.minusYears(10), "CHILD", new boolean[] {true, true})
                .andExpect(status().isCreated());
        addMember(mom, familyId, "둘째", today.minusYears(8), "CHILD", new boolean[] {true, true})
                .andExpect(status().isCreated());
        JsonNode dadMe = read(mvc.perform(auth(get("/api/v1/me"), dad)));
        assertThat(dadMe.get("profiles").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("구성원 추가는 키 · 몸무게를 받아 프로필에 저장하고, 응답에는 싣지 않는다")
    void 구성원_추가는_키_몸무게를_받아_프로필에_저장하고_응답에는_싣지_않는다() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        Map<String, Object> base = memberBody("첫째", today.minusYears(8), "CHILD", new boolean[] {true, true}, "M");

        JsonNode measured = read(postMember(parent, familyId, with(with(base, "heightCm", 128.5), "weightKg", 27.3))
                .andExpect(status().isCreated()));
        JsonNode unmeasured =
                read(postMember(parent, familyId, with(base, "name", "둘째")).andExpect(status().isCreated()));

        assertThat(measured.has("heightCm")).isFalse();
        assertThat(measured.has("weightKg")).isFalse();
        ProfileDetails saved = profileQuery.findDetails(
                UUID.fromString(measured.get("profileId").asString()));
        assertThat(saved).isNotNull();
        assertThat(saved.heightCm()).isEqualByComparingTo("128.5");
        assertThat(saved.weightKg()).isEqualByComparingTo("27.3");
        ProfileDetails blank = profileQuery.findDetails(
                UUID.fromString(unmeasured.get("profileId").asString()));
        assertThat(blank).isNotNull();
        assertThat(blank.heightCm()).isNull();
        assertThat(blank.weightKg()).isNull();
    }

    @Test
    @DisplayName("구성원 추가의 키 · 몸무게 범위는 측정 등록과 같다 — 키 30~230 · 몸무게 5~250, 0 은 범위 밖")
    void 구성원_추가의_키_몸무게_범위는_측정_등록과_같다() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        Map<String, Object> base = memberBody("아이", today.minusYears(8), "CHILD", new boolean[] {true, true}, "M");
        Map<String, Object[]> cases = new LinkedHashMap<>();
        cases.put("키 29.9", new Object[] {"heightCm", 29.9});
        cases.put("키 230.1", new Object[] {"heightCm", 230.1});
        cases.put("키 0", new Object[] {"heightCm", 0});
        cases.put("몸무게 4.9", new Object[] {"weightKg", 4.9});
        cases.put("몸무게 250.1", new Object[] {"weightKg", 250.1});
        cases.put("몸무게 0", new Object[] {"weightKg", 0});
        cases.put("키 30", new Object[] {"heightCm", 30});
        cases.put("키 230", new Object[] {"heightCm", 230});
        cases.put("몸무게 5", new Object[] {"weightKg", 5});
        cases.put("몸무게 250", new Object[] {"weightKg", 250});

        Map<String, String> outcomes = new LinkedHashMap<>();
        for (Map.Entry<String, Object[]> it : cases.entrySet()) {
            Object[] field = it.getValue();
            outcomes.put(it.getKey(), outcome(postMember(parent, familyId, with(base, (String) field[0], field[1]))));
        }

        Map<String, String> expected = new LinkedHashMap<>();
        for (String outside : List.of("키 29.9", "키 230.1", "키 0", "몸무게 4.9", "몸무게 250.1", "몸무게 0")) {
            expected.put(outside, "400 BAD_REQUEST");
        }
        for (String edge : List.of("키 30", "키 230", "몸무게 5", "몸무게 250")) {
            expected.put(edge, "201");
        }
        assertThat(outcomes).isEqualTo(expected);
    }

    @Test
    @DisplayName("PATCH /profiles/{id} — 보호자가 이름 · 생년월일 · 성별을 고치고, 만 14세 미만이 되면 동의가 필요한 상태가 바로 실린다")
    void 프로필_고치기() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        String ownerId = ownerProfileId(parent);
        String teenId = read(addMember(parent, familyId, "큰애", today.minusYears(20), "CHILD", null, "F")
                        .andExpect(status().isCreated()))
                .get("profileId")
                .asString();

        JsonNode edited = read(editProfile(
                        parent,
                        teenId,
                        Map.of("name", "서연", "birthDate", today.minusYears(10).toString(), "sex", "M"))
                .andExpect(status().isOk()));
        assertThat(edited.get("profileId").asString()).isEqualTo(teenId);
        assertThat(edited.get("name").asString()).isEqualTo("서연");
        assertThat(edited.get("sex").asString()).isEqualTo("M");
        assertThat(edited.get("ageGroup").asString()).isEqualTo("유소년");
        assertThat(edited.get("consentRequired").asBoolean()).isTrue();
        assertThat(edited.get("consentGiven").asBoolean()).isFalse();
        assertThat(edited.get("measurable").asBoolean()).isFalse();
        JsonNode listed = profileIn(parent, familyId, teenId);
        assertThat(listed.get("name").asString()).isEqualTo("서연");
        assertThat(listed.get("consentGiven").asBoolean()).isFalse();
        ProfileDetails saved = profileQuery.findDetails(UUID.fromString(teenId));
        assertThat(saved).isNotNull();
        assertThat(saved.birthDate()).isEqualTo(today.minusYears(10));

        // 빠진 칸은 그대로 — 자기 프로필은 고친다
        JsonNode renamed =
                read(editProfile(parent, ownerId, Map.of("name", "엄마2")).andExpect(status().isOk()));
        assertThat(renamed.get("name").asString()).isEqualTo("엄마2");
        assertThat(renamed.get("sex").asString()).isEqualTo("F");

        Map<String, String> outcomes = new LinkedHashMap<>();
        outcomes.put("빈 이름", outcome(editProfile(parent, teenId, Map.of("name", ""))));
        outcomes.put("공백 이름", outcome(editProfile(parent, teenId, Map.of("name", "   "))));
        outcomes.put("21자 이름", outcome(editProfile(parent, teenId, Map.of("name", "가".repeat(21)))));
        outcomes.put(
                "미래 생일",
                outcome(editProfile(
                        parent, teenId, Map.of("birthDate", today.plusDays(1).toString()))));
        outcomes.put("모르는 성별", outcome(editProfile(parent, teenId, Map.of("sex", "X"))));
        outcomes.put(
                "보호자 생일을 13살로",
                outcome(editProfile(
                        parent,
                        ownerId,
                        Map.of("birthDate", today.minusYears(13).toString()))));
        outcomes.put("없는 프로필", outcome(editProfile(parent, UUID.randomUUID().toString(), Map.of("name", "누구"))));
        outcomes.put("다른 가족", outcome(editProfile(devLogin(), teenId, Map.of("name", "침입"))));
        assertThat(outcomes)
                .containsExactly(
                        Map.entry("빈 이름", "400 BAD_REQUEST"),
                        Map.entry("공백 이름", "400 BAD_REQUEST"),
                        Map.entry("21자 이름", "400 BAD_REQUEST"),
                        Map.entry("미래 생일", "400 BAD_REQUEST"),
                        Map.entry("모르는 성별", "400 BAD_REQUEST"),
                        Map.entry("보호자 생일을 13살로", "422 UNDER_14_NOT_ALLOWED"),
                        Map.entry("없는 프로필", "404 PROFILE_NOT_FOUND"),
                        Map.entry("다른 가족", "403 NOT_SAME_FAMILY"));

        // 계정이 붙은 아이 — 보호자도 못 고치고(403 FORBIDDEN), 아이 계정은 보호자가 아니다(403 NOT_A_PARENT)
        String code = read(invite(parent, teenId).andExpect(status().isCreated()))
                .get("claimCode")
                .asString();
        Session kid = devLoginWithCode(code);
        claim(kid, code).andExpect(status().isOk());
        assertThat(outcome(editProfile(parent, teenId, Map.of("name", "바꿈")))).isEqualTo("403 FORBIDDEN");
        assertThat(outcome(editProfile(kid, teenId, Map.of("name", "바꿈")))).isEqualTo("403 NOT_A_PARENT");
        assertThat(profileIn(parent, familyId, teenId).get("name").asString()).isEqualTo("서연");
    }

    @Test
    @DisplayName("만 14세 미만은 가족을 만들거나 PARENT 로 들어오지 못하고, 자기 동의는 SELF_CONSENT 로 막는다")
    void 만_14세_미만_보호자와_자기_동의를_막는다() throws Exception {
        Session kid = devLogin();
        mvc.perform(json(
                        auth(post("/api/v1/families"), kid),
                        Map.of(
                                "familyName",
                                "아이 가족",
                                "owner",
                                Map.of(
                                        "name",
                                        "아이",
                                        "birthDate",
                                        today.minusYears(11).toString(),
                                        "sex",
                                        "F"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("UNDER_14_NOT_ALLOWED"));
        assertThat(read(mvc.perform(auth(get("/api/v1/me"), kid)))
                        .get("nextStep")
                        .asString())
                .isEqualTo("CREATE_FAMILY");

        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        String ownerId = ownerProfileId(parent);
        assertThat(outcome(addMember(
                        parent, familyId, "어린 보호자", today.minusYears(13), "PARENT", new boolean[] {true, true})))
                .isEqualTo("422 UNDER_14_NOT_ALLOWED");
        assertThat(outcome(addMember(parent, familyId, "아빠", today.minusYears(14), "PARENT")))
                .isEqualTo("201");

        assertThat(outcome(consent(parent, ownerId, true, true))).isEqualTo("403 SELF_CONSENT");
        assertThat(outcome(consent(parent, ownerId, false, false))).isEqualTo("403 SELF_CONSENT");
        assertThat(profileIn(parent, familyId, ownerId).get("consentGiven").asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("거둔 동의는 만 14세 이상이어도 다시 동의할 때까지 막히고, 동의를 바꿀 때마다 consent_events 에 한 줄씩 남는다")
    void 거둔_동의는_만_14세_이상도_막히고_이력이_남는다() throws Exception {
        Session parent = devLogin();
        String familyId = createFamily(parent).get("familyId").asString();
        String teenId = read(addMember(parent, familyId, "큰애", today.minusYears(15), "CHILD"))
                .get("profileId")
                .asString();
        String childId = read(addMember(
                        parent, familyId, "첫째", today.minusYears(10), "CHILD", new boolean[] {true, true}))
                .get("profileId")
                .asString();

        JsonNode revoked = read(consent(parent, teenId, false, false).andExpect(status().isOk()));
        assertThat(revoked.get("consentGiven").asBoolean()).isFalse();
        assertThat(revoked.get("measurable").asBoolean()).isFalse();
        JsonNode blocked = profileIn(parent, familyId, teenId);
        assertThat(blocked.get("consentRequired").asBoolean()).isTrue();
        assertThat(blocked.get("consentGiven").asBoolean()).isFalse();
        assertThat(blocked.get("measurable").asBoolean()).isFalse();

        JsonNode regranted = read(consent(parent, teenId, true, true).andExpect(status().isOk()));
        assertThat(regranted.get("consentGiven").asBoolean()).isTrue();
        assertThat(profileIn(parent, familyId, teenId).get("consentRequired").asBoolean())
                .isFalse();

        consent(parent, childId, true, false).andExpect(status().isOk());
        consent(parent, childId, true, true).andExpect(status().isOk());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select actor_user_id, kind, personal_data, health_data from consent_events"
                        + " where profile_id = ? order by occurred_at, id",
                UUID.fromString(childId));
        assertThat(rows)
                .extracting(it -> it.get("KIND"), it -> it.get("PERSONAL_DATA"), it -> it.get("HEALTH_DATA"))
                .containsExactly(
                        tuple("GRANTED", true, true), tuple("REVOKED", true, false), tuple("GRANTED", true, true));
        assertThat(rows).allSatisfy(it -> assertThat(it.get("ACTOR_USER_ID")).isEqualTo(parent.userId()));
        assertThat(jdbc.queryForList(
                        "select kind from consent_events where profile_id = ? order by occurred_at, id",
                        String.class,
                        UUID.fromString(teenId)))
                .containsExactly("REVOKED", "GRANTED");
    }
}
