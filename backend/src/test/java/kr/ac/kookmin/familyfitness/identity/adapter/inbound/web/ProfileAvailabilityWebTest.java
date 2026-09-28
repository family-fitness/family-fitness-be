package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilityQuery;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway(V142 profile_availability_slots) + 실제 보안 필터 · identity 저장소 위에서
 * GET · PUT /profiles/{profileId}/availability 를 끝까지 돈다. 계정은 users 행을 꽂고 프로필에 붙인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileAvailabilityWebTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AvailabilityQuery availabilityQuery;

    private UUID momUser;
    private UUID childUser;
    private UUID otherParentUser;
    private UUID momId;
    private UUID kidId;

    @BeforeEach
    void setUp() {
        UUID familyId = rows.family();
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        momUser = attachUser(momId);
        childUser = attachUser(kidId);

        UUID otherFamily = rows.family("옆집");
        otherParentUser =
                attachUser(rows.profile(otherFamily, LocalDate.of(1985, 7, 7), Sex.F, ProfileRole.PARENT, "옆집 엄마"));
    }

    /** 계정 행을 만들어 프로필에 붙인다 */
    private UUID attachUser(UUID profileId) {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update(
                "insert into users (id, provider, provider_user_id, email, status, created_at, updated_at)"
                        + " values (?, 'DEV', ?, null, 'ACTIVE', ?, ?)",
                userId,
                "availability-" + userId,
                now,
                now);
        jdbc.update("update profiles set user_id = ? where id = ?", userId, profileId);
        return userId;
    }

    private String path(UUID profileId) {
        return "/api/v1/profiles/" + profileId + "/availability";
    }

    private ResultActions read(UUID userId, UUID profileId) throws Exception {
        return mvc.perform(get(path(profileId)).header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)));
    }

    private ResultActions write(UUID userId, UUID profileId, String body) throws Exception {
        return mvc.perform(put(path(profileId))
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<Map<String, Object>> storedRows(UUID profileId) {
        return jdbc.queryForList(
                "select day_of_week, start_time, minutes, created_by from profile_availability_slots"
                        + " where profile_id = ? order by day_of_week",
                profileId);
    }

    @Test
    @DisplayName("적어 둔 것이 없으면 200 { profileId, slots: [] } — 404 가 아니다")
    void emptyWeek() throws Exception {
        read(childUser, kidId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(kidId.toString()))
                .andExpect(jsonPath("$.slots", empty()));
    }

    @Test
    @DisplayName("보호자가 적으면 요일 차례로 답하고, 아이 계정도 같은 한 주를 본다 · 다른 모듈은 AvailabilityQuery 로 읽는다")
    void parentWritesChildReads() throws Exception {
        String body = """
                {"slots":[{"day":"FRI","start":"19:00","minutes":20},
                          {"day":"MON","start":"09:05","minutes":120}]}""";

        write(momUser, kidId, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(kidId.toString()))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].day").value("MON"))
                .andExpect(jsonPath("$.slots[0].start").value("09:05"))
                .andExpect(jsonPath("$.slots[0].minutes").value(120))
                .andExpect(jsonPath("$.slots[1].day").value("FRI"))
                .andExpect(jsonPath("$.slots[1].start").value("19:00"))
                .andExpect(jsonPath("$.slots[1].minutes").value(20));

        read(childUser, kidId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].day").value("MON"))
                .andExpect(jsonPath("$.slots[0].start").value("09:05"))
                .andExpect(jsonPath("$.slots[1].day").value("FRI"));

        assertThat(availabilityQuery.slotsOf(kidId))
                .containsExactly(
                        new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(9, 5), 120),
                        new AvailabilitySlot(DayOfWeek.FRIDAY, LocalTime.of(19, 0), 20));
        assertThat(storedRows(kidId))
                .allSatisfy(row -> assertThat(row.get("created_by")).isEqualTo(momId));
    }

    @Test
    @DisplayName("다시 적으면 DB 의 한 주가 통째로 바뀐다 — 빈 목록이면 행이 모두 지워진다")
    void replacesRows() throws Exception {
        write(momUser, kidId, """
                        {"slots":[{"day":"MON","start":"19:00","minutes":20},
                                  {"day":"WED","start":"19:00","minutes":20}]}""").andExpect(status().isOk());

        write(momUser, kidId, """
                        {"slots":[{"day":"WED","start":"18:30","minutes":40},
                                  {"day":"SAT","start":"10:00","minutes":30}]}""").andExpect(status().isOk());
        assertThat(storedRows(kidId))
                .extracting(row -> row.get("day_of_week") + " " + row.get("start_time") + " " + row.get("minutes"))
                .containsExactly("SAT 10:00:00 30", "WED 18:30:00 40");

        write(momUser, kidId, "{\"slots\":[]}").andExpect(status().isOk()).andExpect(jsonPath("$.slots", empty()));
        assertThat(storedRows(kidId)).isEmpty();
    }

    @Test
    @DisplayName("값이 틀리면 400 INVALID_SLOT(목과 같은 상태)이고 적어 둔 한 주는 그대로다")
    void invalidSlot() throws Exception {
        write(momUser, kidId, "{\"slots\":[{\"day\":\"MON\",\"start\":\"19:00\",\"minutes\":20}]}")
                .andExpect(status().isOk());

        for (String slot : List.of(
                "{\"day\":\"TUE\",\"start\":\"19:00\",\"minutes\":121}",
                "{\"day\":\"TUE\",\"start\":\"19:00\",\"minutes\":4}",
                "{\"day\":\"TUE\",\"start\":\"19:00\",\"minutes\":20.5}",
                "{\"day\":\"tue\",\"start\":\"19:00\",\"minutes\":20}",
                "{\"day\":\"TUE\",\"start\":\"7:00\",\"minutes\":20}",
                "{\"day\":\"TUE\",\"start\":\"24:00\",\"minutes\":20}",
                "{\"start\":\"19:00\",\"minutes\":20}",
                "null")) {
            write(momUser, kidId, "{\"slots\":[{\"day\":\"SUN\",\"start\":\"10:00\",\"minutes\":30}," + slot + "]}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_SLOT"));
        }
        assertThat(storedRows(kidId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 요일 두 칸은 400 INVALID_SLOT — 기본 키 위반(409)이나 500 으로 새지 않는다")
    void duplicateDay() throws Exception {
        write(momUser, kidId, """
                        {"slots":[{"day":"MON","start":"07:00","minutes":10},
                                  {"day":"MON","start":"19:00","minutes":20}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SLOT"));
        assertThat(storedRows(kidId)).isEmpty();
    }

    @Test
    @DisplayName("slots 가 빠지면 400 BAD_REQUEST — 빈 본문으로 한 주가 지워지지 않는다")
    void missingSlots() throws Exception {
        write(momUser, kidId, "{\"slots\":[{\"day\":\"MON\",\"start\":\"19:00\",\"minutes\":20}]}")
                .andExpect(status().isOk());

        write(momUser, kidId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        assertThat(storedRows(kidId)).hasSize(1);
    }

    @Test
    @DisplayName("아이 계정이 바꾸면 403 NOT_A_PARENT, 다른 가족은 보기도 403 NOT_SAME_FAMILY, 없는 프로필은 404")
    void permissions() throws Exception {
        String body = "{\"slots\":[{\"day\":\"MON\",\"start\":\"19:00\",\"minutes\":20}]}";

        write(childUser, kidId, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        read(otherParentUser, kidId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        write(otherParentUser, kidId, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        read(momUser, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));
        mvc.perform(get(path(kidId))).andExpect(status().isUnauthorized());
        assertThat(storedRows(kidId)).isEmpty();
    }
}
