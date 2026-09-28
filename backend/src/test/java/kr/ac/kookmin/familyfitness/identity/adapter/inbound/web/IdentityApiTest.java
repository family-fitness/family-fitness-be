package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("birthDate", birthDate.toString());
        body.put("sex", sex);
        body.put("role", role);
        if (consent != null) {
            body.put("guardianConsent", Map.of("personalData", consent[0], "healthData", consent[1]));
        }
        return mvc.perform(json(auth(post("/api/v1/families/" + familyId + "/profiles"), session), body));
    }

    private ResultActions invite(Session session, String profileId) throws Exception {
        return mvc.perform(auth(post("/api/v1/profiles/" + profileId + "/invite"), session));
    }

    private ResultActions claim(Session session, String code) throws Exception {
        return mvc.perform(json(auth(post("/api/v1/profiles/claim"), session), Map.of("claimCode", code)));
    }

    private JsonNode read(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
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

        // 참여 수준: 본인 PARENT 프로필만
        JsonNode changed = read(mvc.perform(json(
                        auth(patch("/api/v1/profiles/" + dadId + "/support-mode"), dadUser),
                        Map.of("supportMode", "WEEKEND")))
                .andExpect(status().isOk()));
        assertThat(changed.get("supportMode").asString()).isEqualTo("WEEKEND");
        assertThat(changed.get("profileId").asString()).isEqualTo(dadId);
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
        assertThat(created.get("emoji").asString()).isEqualTo("💪");
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
}
