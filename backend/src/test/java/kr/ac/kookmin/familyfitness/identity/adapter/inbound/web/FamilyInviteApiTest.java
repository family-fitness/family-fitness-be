package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가족 초대(초대 먼저). 보호자가 역할만 정해 코드를 내고, 코드로 들어온 사람이 이름과 생년월일을 넣어 자기 프로필을 만든다.
 * H2(PostgreSQL 모드) + Flyway + 실제 보안 필터를 거친다. 계정은 개발용 로그인으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FamilyInviteApiTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now();

    private record Session(UUID userId, String bearer) {}

    /** 가족을 만든 보호자와 그 가족. */
    private record Home(Session owner, String familyId, String ownerProfileId) {}

    // ===== 가족 초대 만들기 =====

    @Test
    @DisplayName("보호자가 CHILD 초대를 만들면 201 이고 코드, 역할, 7일 뒤 만료, 가족 id 를 준다. 보낸 동의와 보호자를 표에 남긴다")
    void 보호자가_CHILD_초대를_만든다() throws Exception {
        Home home = home();
        Instant before = Instant.now();

        JsonNode invite = read(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true))
                .andExpect(status().isCreated()));

        String code = invite.get("code").asString();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
        assertThat(invite.get("role").asString()).isEqualTo("CHILD");
        assertThat(invite.get("familyId").asString()).isEqualTo(home.familyId());
        assertThat(Instant.parse(invite.get("expiresAt").asString()))
                .isCloseTo(before.plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));
        Map<String, Object> row = jdbc.queryForMap("select * from family_invites where code = ?", code);
        assertThat(row.get("family_id").toString()).isEqualTo(home.familyId());
        assertThat(row.get("role")).isEqualTo("CHILD");
        assertThat(row.get("consent_personal_data")).isEqualTo(true);
        assertThat(row.get("consent_health_data")).isEqualTo(true);
        assertThat(row.get("consent_by_user_id").toString())
                .isEqualTo(home.owner().userId().toString());
        assertThat(row.get("issued_by_profile_id").toString()).isEqualTo(home.ownerProfileId());
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("claimed_by_user_id")).isNull();
    }

    @Test
    @DisplayName("PARENT 초대는 보호자 동의 없이 만든다. 같은 가족에 초대를 여러 개 내도 코드는 서로 다르다")
    void PARENT_초대는_동의_없이_만든다() throws Exception {
        Home home = home();

        JsonNode first =
                read(createInvite(home.owner(), home.familyId(), "PARENT", null).andExpect(status().isCreated()));
        JsonNode second =
                read(createInvite(home.owner(), home.familyId(), "PARENT", null).andExpect(status().isCreated()));

        assertThat(first.get("role").asString()).isEqualTo("PARENT");
        assertThat(first.get("code").asString()).isNotEqualTo(second.get("code").asString());
        Map<String, Object> row = jdbc.queryForMap(
                "select * from family_invites where code = ?", first.get("code").asString());
        assertThat(row.get("consent_personal_data")).isNull();
        assertThat(row.get("consent_health_data")).isNull();
        assertThat(row.get("consent_by_user_id")).isNull();
    }

    @Test
    @DisplayName("가족 초대는 그 가족 보호자만 만든다. 아이 계정 403 NOT_A_PARENT, 다른 가족 403 NOT_SAME_FAMILY, 없는 가족 404")
    void 가족_초대는_그_가족_보호자만_만든다() throws Exception {
        Home home = home();
        Session kid = childAccount(home);
        Session otherParent = home().owner();
        Session noFamily = devLogin();

        createInvite(kid, home.familyId(), "PARENT", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        createInvite(otherParent, home.familyId(), "PARENT", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        createInvite(noFamily, home.familyId(), "CHILD", consent(true, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        createInvite(home.owner(), UUID.randomUUID().toString(), "PARENT", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_FOUND"));
        assertThat(count("select count(*) from family_invites where family_id = ?", UUID.fromString(home.familyId())))
                .isZero();
    }

    @Test
    @DisplayName("CHILD 초대에 보호자 동의가 없거나 하나라도 false 면 422 CONSENT_REQUIRED, 역할이 없으면 400")
    void CHILD_초대에_동의가_없으면_422() throws Exception {
        Home home = home();

        createInvite(home.owner(), home.familyId(), "CHILD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        createInvite(home.owner(), home.familyId(), "CHILD", consent(true, false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        mvc.perform(json(auth(post("/api/v1/families/" + home.familyId() + "/invites"), home.owner()), Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(json(
                        auth(post("/api/v1/families/" + home.familyId() + "/invites"), home.owner()),
                        Map.of("role", "GRANDMA")))
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from family_invites where family_id = ?", UUID.fromString(home.familyId())))
                .isZero();
    }

    // ===== 목록과 취소 =====

    @Test
    @DisplayName("목록은 아직 쓰지 않았고 만료되지 않은 그 가족의 초대만 최근 것부터 싣고, 낸 보호자 이름과 만든 때를 준다")
    void 목록은_살아_있는_초대만_싣는다() throws Exception {
        Home home = home();
        Session dad = parentAccount(home, "아빠");
        String expired = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        String used = codeOf(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true)));
        String momChild = codeOf(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true)));
        String dadParent = codeOf(createInvite(dad, home.familyId(), "PARENT", null));
        Home other = home();
        createInvite(other.owner(), other.familyId(), "PARENT", null).andExpect(status().isCreated());
        jdbc.update(
                "update family_invites set expires_at = ? where code = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                expired);
        jdbc.update(
                "update family_invites set claimed_at = ? where code = ?", OffsetDateTime.now(ZoneOffset.UTC), used);

        JsonNode listed = read(listInvites(home.owner(), home.familyId()).andExpect(status().isOk()));

        List<JsonNode> invites = list(listed.get("invites"));
        assertThat(invites).extracting(it -> it.get("code").asString()).containsExactlyInAnyOrder(momChild, dadParent);
        List<Instant> created = invites.stream()
                .map(it -> Instant.parse(it.get("createdAt").asString()))
                .toList();
        assertThat(created).isSortedAccordingTo(Comparator.reverseOrder());
        JsonNode mine = byCode(invites, momChild);
        assertThat(mine.get("role").asString()).isEqualTo("CHILD");
        assertThat(mine.get("issuedByName").asString()).isEqualTo("엄마");
        assertThat(Instant.parse(mine.get("expiresAt").asString()))
                .isCloseTo(
                        Instant.parse(mine.get("createdAt").asString()).plus(Duration.ofDays(7)),
                        within(1, ChronoUnit.SECONDS));
        assertThat(byCode(invites, dadParent).get("issuedByName").asString()).isEqualTo("아빠");
        // 함께 사는 보호자도 같은 목록을 본다
        assertThat(list(read(listInvites(dad, home.familyId()).andExpect(status().isOk()))
                        .get("invites")))
                .hasSize(2);
    }

    @Test
    @DisplayName("목록은 그 가족 보호자만 본다. 아이 계정 403 NOT_A_PARENT, 다른 가족 403 NOT_SAME_FAMILY, 없는 가족 404")
    void 목록은_그_가족_보호자만_본다() throws Exception {
        Home home = home();
        Session kid = childAccount(home);
        createInvite(home.owner(), home.familyId(), "PARENT", null).andExpect(status().isCreated());

        listInvites(kid, home.familyId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        listInvites(home().owner(), home.familyId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        listInvites(home.owner(), UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_FOUND"));
        // 초대가 하나도 없으면 빈 목록이다
        Home empty = home();
        assertThat(read(listInvites(empty.owner(), empty.familyId()).andExpect(status().isOk()))
                        .get("invites")
                        .isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("보호자가 초대를 취소하면 204 이고 목록과 표에서 빠진다. 다시 취소하거나 쓴 코드, 다른 가족 코드면 404 INVITE_NOT_FOUND")
    void 보호자가_초대를_취소한다() throws Exception {
        Home home = home();
        Session dad = parentAccount(home, "아빠");
        String code = codeOf(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true)));
        String used = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        jdbc.update(
                "update family_invites set claimed_at = ? where code = ?", OffsetDateTime.now(ZoneOffset.UTC), used);
        Home other = home();
        String otherCode = codeOf(createInvite(other.owner(), other.familyId(), "PARENT", null));

        // 엄마가 낸 초대를 아빠가 취소해도 된다. 대소문자는 가리지 않는다
        cancelInvite(dad, home.familyId(), code.toLowerCase()).andExpect(status().isNoContent());

        assertThat(count("select count(*) from family_invites where code = ?", code))
                .isZero();
        assertThat(list(read(listInvites(home.owner(), home.familyId())).get("invites")))
                .isEmpty();
        cancelInvite(home.owner(), home.familyId(), code)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"));
        cancelInvite(home.owner(), home.familyId(), used)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"));
        cancelInvite(home.owner(), home.familyId(), otherCode)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"));
        cancelInvite(home.owner(), home.familyId(), "not-a-code")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"));
        assertThat(count("select count(*) from family_invites where code in (?, ?)", used, otherCode))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("취소는 그 가족 보호자만 한다. 아이 계정 403 NOT_A_PARENT, 다른 가족 403 NOT_SAME_FAMILY, 없는 가족 404")
    void 취소는_그_가족_보호자만_한다() throws Exception {
        Home home = home();
        Session kid = childAccount(home);
        String code = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));

        cancelInvite(kid, home.familyId(), code)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        cancelInvite(home().owner(), home.familyId(), code)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        cancelInvite(home.owner(), UUID.randomUUID().toString(), code)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_FOUND"));
        assertThat(count("select count(*) from family_invites where code = ?", code))
                .isEqualTo(1);
    }

    // ===== 미리 보기 =====

    @Test
    @DisplayName("가족 초대코드 미리 보기는 kind FAMILY 와 역할, 가족 이름, 보낸 보호자를 주고 자리 이름과 연령대는 null 이다")
    void 가족_초대코드_미리_보기() throws Exception {
        Home home = home();
        JsonNode invite = read(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true))
                .andExpect(status().isCreated()));
        String code = invite.get("code").asString();

        JsonNode seat = read(preview(devLogin(), code.toLowerCase()).andExpect(status().isOk()));

        assertThat(seat.get("kind").asString()).isEqualTo("FAMILY");
        assertThat(seat.get("role").asString()).isEqualTo("CHILD");
        assertThat(seat.get("familyName").asString()).isEqualTo("우리 가족");
        assertThat(seat.get("profileName").isNull()).isTrue();
        assertThat(seat.get("ageGroup").isNull()).isTrue();
        assertThat(seat.get("invitedByName").asString()).isEqualTo("엄마");
        assertThat(Instant.parse(seat.get("expiresAt").asString()))
                .isCloseTo(Instant.parse(invite.get("expiresAt").asString()), within(1, ChronoUnit.MILLIS));
        assertThat(seat.has("profileId")).isFalse();
        assertThat(seat.has("familyId")).isFalse();
        String parentCode = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        assertThat(read(preview(devLogin(), parentCode)).get("role").asString()).isEqualTo("PARENT");
    }

    @Test
    @DisplayName("자리 초대코드 미리 보기는 kind PROFILE 이고 지금처럼 자리 이름과 역할, 연령대를 준다")
    void 자리_초대코드_미리_보기는_kind_PROFILE() throws Exception {
        Home home = home();
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("name", "서준");
        member.put("birthDate", today.minusYears(10).toString());
        member.put("sex", "M");
        member.put("role", "CHILD");
        member.put("guardianConsent", consent(true, true));
        String childId = read(mvc.perform(json(
                                auth(post("/api/v1/families/" + home.familyId() + "/profiles"), home.owner()), member))
                        .andExpect(status().isCreated()))
                .get("profileId")
                .asString();
        String code = read(mvc.perform(auth(post("/api/v1/profiles/" + childId + "/invite"), home.owner())))
                .get("claimCode")
                .asString();

        JsonNode seat = read(preview(devLogin(), code).andExpect(status().isOk()));

        assertThat(seat.get("kind").asString()).isEqualTo("PROFILE");
        assertThat(seat.get("role").asString()).isEqualTo("CHILD");
        assertThat(seat.get("profileName").asString()).isEqualTo("서준");
        assertThat(seat.get("ageGroup").asString()).isEqualTo("유소년");
        assertThat(seat.get("invitedByName").asString()).isEqualTo("엄마");
    }

    @Test
    @DisplayName("가족 초대코드 미리 보기: 쓴 코드 409 ALREADY_CLAIMED, 만료 410 CODE_EXPIRED, 취소한 코드 404 CODE_NOT_FOUND")
    void 가족_초대코드_미리_보기_오류() throws Exception {
        Home home = home();
        String used = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        String expired = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        String cancelled = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        jdbc.update(
                "update family_invites set claimed_at = ? where code = ?", OffsetDateTime.now(ZoneOffset.UTC), used);
        jdbc.update(
                "update family_invites set expires_at = ? where code = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1),
                expired);
        cancelInvite(home.owner(), home.familyId(), cancelled).andExpect(status().isNoContent());
        Session newcomer = devLogin();

        preview(newcomer, used)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
        preview(newcomer, expired)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("CODE_EXPIRED"));
        preview(newcomer, cancelled)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CODE_NOT_FOUND"));
    }

    // ===== 가족 초대코드로 들어오기 =====

    @Test
    @DisplayName("PARENT 초대로 들어온 계정은 넣은 정보로 보호자 프로필이 생기고 SUPPORT_MODE 로 간다. 오너는 아니다")
    void PARENT_초대로_들어온다() throws Exception {
        Home home = home();
        String code = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        Session dad = devLogin();
        Map<String, Object> body = joinBody(code, "아빠", "1986-01-01", "M");
        body.put("heightCm", 175.5);
        body.put("weightKg", 70);

        JsonNode joined = read(claim(dad, body).andExpect(status().isOk()));

        String dadId = joined.get("profileId").asString();
        assertThat(joined.get("familyId").asString()).isEqualTo(home.familyId());
        assertThat(joined.get("role").asString()).isEqualTo("PARENT");
        assertThat(joined.get("nextStep").asString()).isEqualTo("SUPPORT_MODE");
        JsonNode me = read(mvc.perform(auth(get("/api/v1/me"), dad)).andExpect(status().isOk()));
        // 참여 방식을 고르기 전에 닫고 다시 열어도 SUPPORT_MODE 다
        assertThat(me.get("nextStep").asString()).isEqualTo("SUPPORT_MODE");
        assertThat(me.get("selfProfileId").asString()).isEqualTo(dadId);
        JsonNode self = me.get("profiles").get(0);
        assertThat(self.get("name").asString()).isEqualTo("아빠");
        assertThat(self.get("role").asString()).isEqualTo("PARENT");
        assertThat(self.get("sex").asString()).isEqualTo("M");
        assertThat(self.get("ageGroup").asString()).isEqualTo("성인");
        assertThat(self.get("isOwner").asBoolean()).isFalse();
        assertThat(self.get("hasAccount").asBoolean()).isTrue();
        assertThat(self.get("inviteStatus").asString()).isEqualTo("CLAIMED");
        assertThat(self.get("familyId").asString()).isEqualTo(home.familyId());
        Map<String, Object> row = jdbc.queryForMap("select * from profiles where id = ?", UUID.fromString(dadId));
        assertThat(row.get("user_id").toString()).isEqualTo(dad.userId().toString());
        assertThat(row.get("birth_date").toString()).isEqualTo("1986-01-01");
        assertThat(new java.math.BigDecimal(row.get("height_cm").toString())).isEqualByComparingTo("175.5");
        assertThat(new java.math.BigDecimal(row.get("weight_kg").toString())).isEqualByComparingTo("70");
        assertThat(row.get("claim_code")).isEqualTo(code);
        assertThat(row.get("claim_code_issued_by").toString()).isEqualTo(home.ownerProfileId());
        Map<String, Object> invite = jdbc.queryForMap("select * from family_invites where code = ?", code);
        assertThat(invite.get("claimed_at")).isNotNull();
        assertThat(invite.get("claimed_by_user_id").toString())
                .isEqualTo(dad.userId().toString());
        // 쓴 코드는 목록에서 빠지고 다시 쓰지 못한다
        assertThat(list(read(listInvites(home.owner(), home.familyId())).get("invites")))
                .isEmpty();
        claim(devLogin(), joinBody(code, "삼촌", "1990-01-01", "M"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
        preview(devLogin(), code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
        // 들어온 보호자는 가족 초대를 낼 수 있다
        createInvite(dad, home.familyId(), "CHILD", consent(true, true)).andExpect(status().isCreated());
        // 참여 방식을 고르면 홈으로 간다
        mvc.perform(json(
                        auth(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                        "/api/v1/profiles/" + dadId + "/support-mode"),
                                dad),
                        Map.of("supportMode", "WEEKEND")))
                .andExpect(status().isOk());
        assertThat(read(mvc.perform(auth(get("/api/v1/me"), dad)))
                        .get("nextStep")
                        .asString())
                .isEqualTo("HOME");
    }

    @Test
    @DisplayName("CHILD 초대로 들어온 아이 프로필에는 초대 때 받은 보호자 동의가 들어가고, 동의 이력의 동의자는 초대한 보호자다")
    void CHILD_초대로_들어온_아이에게_동의가_들어간다() throws Exception {
        Home home = home();
        String code = codeOf(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true)));
        Object issuedAt = jdbc.queryForMap("select created_at from family_invites where code = ?", code)
                .get("created_at");
        Session kid = devLogin();

        JsonNode joined =
                read(claim(kid, joinBody(code, "하윤", today.minusYears(11).toString(), "F"))
                        .andExpect(status().isOk()));

        UUID kidId = UUID.fromString(joined.get("profileId").asString());
        assertThat(joined.get("role").asString()).isEqualTo("CHILD");
        assertThat(joined.get("nextStep").asString()).isEqualTo("HOME");
        JsonNode self = read(mvc.perform(auth(get("/api/v1/me"), kid)).andExpect(status().isOk()))
                .get("profiles")
                .get(0);
        assertThat(self.get("ageGroup").asString()).isEqualTo("유소년");
        assertThat(self.get("consentRequired").asBoolean()).isTrue();
        assertThat(self.get("consentGiven").asBoolean()).isTrue();
        assertThat(self.get("measurable").asBoolean()).isTrue();
        assertThat(self.get("isOwner").asBoolean()).isFalse();
        Map<String, Object> row = jdbc.queryForMap("select * from profiles where id = ?", kidId);
        assertThat(row.get("consent_personal_at")).isEqualTo(issuedAt);
        assertThat(row.get("consent_health_at")).isEqualTo(issuedAt);
        assertThat(row.get("consent_by_user_id").toString())
                .isEqualTo(home.owner().userId().toString());
        assertThat(row.get("consent_revoked_at")).isNull();
        List<Map<String, Object>> events =
                jdbc.queryForList("select * from consent_events where profile_id = ?", kidId);
        assertThat(events).singleElement().satisfies(it -> {
            assertThat(it.get("kind")).isEqualTo("GRANTED");
            assertThat(it.get("actor_user_id").toString())
                    .isEqualTo(home.owner().userId().toString());
            assertThat(it.get("personal_data")).isEqualTo(true);
            assertThat(it.get("health_data")).isEqualTo(true);
            assertThat(it.get("occurred_at")).isEqualTo(issuedAt);
        });
        // 보호자가 보는 구성원 목록에도 동의가 있는 아이로 보인다
        JsonNode members =
                read(mvc.perform(auth(get("/api/v1/families/" + home.familyId() + "/profiles"), home.owner()))
                        .andExpect(status().isOk()));
        assertThat(list(members.get("profiles")))
                .filteredOn(it -> it.get("profileId").asString().equals(kidId.toString()))
                .singleElement()
                .satisfies(it -> {
                    assertThat(it.get("name").asString()).isEqualTo("하윤");
                    assertThat(it.get("consentGiven").asBoolean()).isTrue();
                    assertThat(it.get("hasAccount").asBoolean()).isTrue();
                });
    }

    @Test
    @DisplayName("가족 초대코드인데 이름, 생년월일, 성별 가운데 하나라도 빠지면 400 BAD_REQUEST 이고 코드는 그대로 남는다")
    void 정보가_빠지면_400() throws Exception {
        Home home = home();
        String code = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        Session dad = devLogin();

        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("claimCode", code),
                without(joinBody(code, "아빠", "1986-01-01", "M"), "name"),
                without(joinBody(code, "아빠", "1986-01-01", "M"), "birthDate"),
                without(joinBody(code, "아빠", "1986-01-01", "M"), "sex"),
                joinBody(code, " ", "1986-01-01", "M"))) {
            claim(dad, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }
        // 미래 생년월일, 범위 밖 키도 400 이다
        claim(dad, joinBody(code, "아빠", today.plusDays(1).toString(), "M")).andExpect(status().isBadRequest());
        Map<String, Object> tooTall = joinBody(code, "아빠", "1986-01-01", "M");
        tooTall.put("heightCm", 300);
        claim(dad, tooTall).andExpect(status().isBadRequest());

        assertThat(count("select count(*) from profiles where user_id = ?", dad.userId()))
                .isZero();
        assertThat(jdbc.queryForMap("select claimed_at from family_invites where code = ?", code)
                        .get("claimed_at"))
                .isNull();
        claim(dad, joinBody(code, "아빠", "1986-01-01", "M")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PARENT 초대에 만 14세 미만 생년월일이면 422 UNDER_14_NOT_ALLOWED 이고, CHILD 초대는 만 14세 이상도 들어온다")
    void PARENT_초대에_만_14세_미만이면_422() throws Exception {
        Home home = home();
        String parentCode = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        String childCode = codeOf(createInvite(home.owner(), home.familyId(), "CHILD", consent(true, true)));
        Session teen = devLogin();

        claim(teen, joinBody(parentCode, "형", today.minusYears(13).toString(), "M"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("UNDER_14_NOT_ALLOWED"));
        assertThat(jdbc.queryForMap("select claimed_at from family_invites where code = ?", parentCode)
                        .get("claimed_at"))
                .isNull();

        JsonNode joined =
                read(claim(teen, joinBody(childCode, "형", today.minusYears(15).toString(), "M"))
                        .andExpect(status().isOk()));
        assertThat(joined.get("role").asString()).isEqualTo("CHILD");
    }

    @Test
    @DisplayName(
            "가족 초대코드 판정 차례는 자리 초대코드와 같다: 만료 410, 같은 가족 409 ALREADY_MEMBER, 다른 가족 409 ALREADY_IN_FAMILY, 그 뒤에 정보 400")
    void 가족_초대코드_판정_차례() throws Exception {
        Home home = home();
        String code = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        String expired = codeOf(createInvite(home.owner(), home.familyId(), "PARENT", null));
        jdbc.update(
                "update family_invites set expires_at = ? where code = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1),
                expired);
        Session kid = childAccount(home);
        Session otherOwner = home().owner();
        Session fresh = devLogin();

        // 정보가 빠졌어도 코드와 계정 판정이 먼저다
        claim(fresh, Map.of("claimCode", expired))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("CODE_EXPIRED"));
        claim(fresh, joinBody(expired, "아빠", "1986-01-01", "M"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("CODE_EXPIRED"));
        claim(home.owner(), Map.of("claimCode", code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));
        claim(kid, joinBody(code, "첫째", "1986-01-01", "M"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));
        claim(otherOwner, joinBody(code, "엄마", "1988-03-01", "F"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_IN_FAMILY"));
        claim(fresh, Map.of("claimCode", code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        claim(fresh, Map.of("claimCode", "ZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CODE_NOT_FOUND"));

        assertThat(count("select count(*) from profiles where family_id = ?", UUID.fromString(home.familyId())))
                .isEqualTo(2);
        claim(fresh, joinBody(code, "아빠", "1986-01-01", "M")).andExpect(status().isOk());
        claim(devLogin(), joinBody(code, "삼촌", "1990-01-01", "M"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
    }

    @Test
    @DisplayName("자리 초대코드는 지금처럼 코드만 받고, 함께 온 이름과 생년월일은 쓰지 않는다")
    void 자리_초대코드는_정보를_쓰지_않는다() throws Exception {
        Home home = home();
        Session dad = parentAccount(home, "아빠");

        JsonNode me = read(mvc.perform(auth(get("/api/v1/me"), dad)).andExpect(status().isOk()));

        assertThat(me.get("profiles").get(0).get("name").asString()).isEqualTo("아빠");
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("name", "둘째");
        member.put("birthDate", today.minusYears(9).toString());
        member.put("sex", "F");
        member.put("role", "CHILD");
        member.put("guardianConsent", consent(true, true));
        String seatId = read(mvc.perform(json(
                                auth(post("/api/v1/families/" + home.familyId() + "/profiles"), home.owner()), member))
                        .andExpect(status().isCreated()))
                .get("profileId")
                .asString();
        String seatCode = read(mvc.perform(auth(post("/api/v1/profiles/" + seatId + "/invite"), home.owner())))
                .get("claimCode")
                .asString();
        Session second = devLogin();
        JsonNode claimed = read(
                claim(second, joinBody(seatCode, "다른 이름", "2000-01-01", "M")).andExpect(status().isOk()));
        assertThat(claimed.get("profileId").asString()).isEqualTo(seatId);
        JsonNode self = read(mvc.perform(auth(get("/api/v1/me"), second)))
                .get("profiles")
                .get(0);
        assertThat(self.get("name").asString()).isEqualTo("둘째");
        assertThat(self.get("sex").asString()).isEqualTo("F");
    }

    // ===== 도우미 =====

    private Session devLogin() throws Exception {
        String subject = "invite-" + UUID.randomUUID();
        JsonNode body = read(mvc.perform(json(post("/api/v1/auth/dev-login"), Map.of("providerUserId", subject)))
                .andExpect(status().isOk()));
        return new Session(
                UUID.fromString(body.get("userId").asString()),
                "Bearer " + body.get("accessToken").asString());
    }

    /** 새 계정이 「우리 가족」 을 만든다. 오너는 1988년생 엄마다. */
    private Home home() throws Exception {
        Session owner = devLogin();
        JsonNode created = read(mvc.perform(json(
                        auth(post("/api/v1/families"), owner),
                        Map.of(
                                "familyName",
                                "우리 가족",
                                "owner",
                                Map.of("name", "엄마", "birthDate", "1988-03-01", "sex", "F"))))
                .andExpect(status().isCreated()));
        return new Home(
                owner,
                created.get("familyId").asString(),
                created.get("ownerProfile").get("profileId").asString());
    }

    /** 보호자가 정보를 넣어 만든 아이 자리에 자리 초대코드로 붙은 아이 계정. */
    private Session childAccount(Home home) throws Exception {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("name", "첫째");
        member.put("birthDate", today.minusYears(10).toString());
        member.put("sex", "M");
        member.put("role", "CHILD");
        member.put("guardianConsent", consent(true, true));
        String childId = read(mvc.perform(json(
                                auth(post("/api/v1/families/" + home.familyId() + "/profiles"), home.owner()), member))
                        .andExpect(status().isCreated()))
                .get("profileId")
                .asString();
        String code = read(mvc.perform(auth(post("/api/v1/profiles/" + childId + "/invite"), home.owner()))
                        .andExpect(status().isCreated()))
                .get("claimCode")
                .asString();
        Session kid = devLogin();
        mvc.perform(json(auth(post("/api/v1/profiles/claim"), kid), Map.of("claimCode", code)))
                .andExpect(status().isOk());
        return kid;
    }

    /** 보호자가 정보를 넣어 만든 보호자 자리에 자리 초대코드로 붙은 보호자 계정. */
    private Session parentAccount(Home home, String name) throws Exception {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("name", name);
        member.put("birthDate", "1986-01-01");
        member.put("sex", "M");
        member.put("role", "PARENT");
        String seatId = read(mvc.perform(json(
                                auth(post("/api/v1/families/" + home.familyId() + "/profiles"), home.owner()), member))
                        .andExpect(status().isCreated()))
                .get("profileId")
                .asString();
        String code = read(mvc.perform(auth(post("/api/v1/profiles/" + seatId + "/invite"), home.owner()))
                        .andExpect(status().isCreated()))
                .get("claimCode")
                .asString();
        Session parent = devLogin();
        mvc.perform(json(auth(post("/api/v1/profiles/claim"), parent), Map.of("claimCode", code)))
                .andExpect(status().isOk());
        return parent;
    }

    private ResultActions createInvite(
            Session session, String familyId, String role, @Nullable Map<String, Object> guardianConsent)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", role);
        if (guardianConsent != null) body.put("guardianConsent", guardianConsent);
        return mvc.perform(json(auth(post("/api/v1/families/" + familyId + "/invites"), session), body));
    }

    private String codeOf(ResultActions created) throws Exception {
        return read(created.andExpect(status().isCreated())).get("code").asString();
    }

    private ResultActions claim(Session session, Map<String, Object> body) throws Exception {
        return mvc.perform(json(auth(post("/api/v1/profiles/claim"), session), body));
    }

    /** 가족 초대코드로 들어올 때의 본문. 키와 몸무게는 뺀다. */
    private static Map<String, Object> joinBody(String code, String name, String birthDate, String sex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("claimCode", code);
        body.put("name", name);
        body.put("birthDate", birthDate);
        body.put("sex", sex);
        return body;
    }

    private static Map<String, Object> without(Map<String, Object> body, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.remove(key);
        return copy;
    }

    private ResultActions preview(Session session, String code) throws Exception {
        return mvc.perform(auth(get("/api/v1/invites/" + code), session));
    }

    private ResultActions listInvites(Session session, String familyId) throws Exception {
        return mvc.perform(auth(get("/api/v1/families/" + familyId + "/invites"), session));
    }

    private ResultActions cancelInvite(Session session, String familyId, String code) throws Exception {
        return mvc.perform(auth(delete("/api/v1/families/" + familyId + "/invites/" + code), session));
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private static JsonNode byCode(List<JsonNode> invites, String code) {
        return invites.stream()
                .filter(it -> it.get("code").asString().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> consent(boolean personal, boolean health) {
        return Map.of("personalData", personal, "healthData", health);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, Session session) {
        return builder.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    private JsonNode read(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }
}
