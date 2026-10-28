package kr.ac.kookmin.familyfitness.fitness.web;

import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.childAccountOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.detailsOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.parentOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.summaryOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway 의 실제 또래 분포 표(V156) 위에서 성인 체력 나이를 끝까지 돈다. 기대값은 같은 표로 따로 셈한 값이다:
 * 남 만 40세가 019 39회 · 020 36회 · 022 210cm · 028 61.4% 이면 항목 나이 41.5 · 39.5 · 42.0 · 32.0, 평균 38.75 → 39세.
 * 012(앉아윗몸앞으로굽히기)는 나이와 관계가 약해 빠진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FitnessAgeWebTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    CheerQuery cheerQuery;

    private final UUID userId = UUID.randomUUID();
    private final UUID kidUserId = UUID.randomUUID();

    private UUID familyId;
    private UUID dadId;
    private UUID childId;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private final LocalDate testedOn = today.minusDays(1);

    /** 측정일 기준 늘 만 40세 */
    private final LocalDate dadBirth = today.minusYears(40).minusMonths(2);

    /** 늘 만 11세(유소년) */
    private final LocalDate childBirth = today.minusYears(11).minusMonths(4);

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        dadId = rows.profile(familyId, dadBirth, Sex.M, ProfileRole.PARENT);
        childId = rows.profile(familyId, childBirth, Sex.F);

        when(familyAccess.requireSameFamilyAsProfile(userId, dadId))
                .thenReturn(summaryOf(dadId, familyId, AgeGroup.ADULT, true, false, true));
        when(familyAccess.requireParentOfProfile(userId, dadId)).thenReturn(parentOf(dadId, familyId));
        when(familyAccess.requireMember(userId, familyId)).thenReturn(parentOf(dadId, familyId));
        when(profileQuery.findDetails(dadId)).thenReturn(detailsOf(dadId, familyId, dadBirth, Sex.M, null, null, true));

        when(familyAccess.requireSameFamilyAsProfile(userId, childId))
                .thenReturn(summaryOf(childId, familyId, AgeGroup.YOUTH));
        when(familyAccess.requireParentOfProfile(userId, childId)).thenReturn(parentOf(dadId, familyId));
        when(profileQuery.findDetails(childId))
                .thenReturn(detailsOf(childId, familyId, childBirth, Sex.F, null, null, true));

        when(familyAccess.requireParentOfProfile(kidUserId, dadId)).thenThrow(new NotAParentException());
    }

    private void register(UUID profileId, String items) throws Exception {
        mvc.perform(post("/api/v1/profiles/" + profileId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testedOn\":\"" + testedOn + "\",\"source\":\"SELF_INPUT\",\"heightCm\":175,"
                                + "\"weightKg\":72,\"items\":[" + items + "]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("성인 보호자의 최근 측정으로 체력 나이를 낸다")
    void 성인_체력_나이() throws Exception {
        register(
                dadId,
                "{\"itemCode\":\"019\",\"value\":39},{\"itemCode\":\"020\",\"value\":36},"
                        + "{\"itemCode\":\"022\",\"value\":210},{\"itemCode\":\"028\",\"value\":61.4},"
                        + "{\"itemCode\":\"012\",\"value\":10}");

        mvc.perform(get("/api/v1/profiles/" + dadId + "/fitness-age")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.testedOn").value(testedOn.toString()))
                .andExpect(jsonPath("$.age").value(40))
                .andExpect(jsonPath("$.fitnessAge").value(39))
                .andExpect(jsonPath("$.ageGap").value(-1))
                .andExpect(jsonPath("$.minItems").value(2))
                .andExpect(jsonPath("$.range.min").value(19))
                .andExpect(jsonPath("$.range.max").value(64))
                .andExpect(jsonPath("$.items[*].itemCode").value(contains("019", "020", "022", "028")))
                .andExpect(jsonPath("$.items[*].itemAge").value(contains(41.5, 39.5, 42.0, 32.0)))
                .andExpect(jsonPath("$.items[0].bound").value("NONE"))
                .andExpect(jsonPath("$.excludedItems", hasSize(1)))
                .andExpect(jsonPath("$.excludedItems[0].itemCode").value("012"))
                .andExpect(jsonPath("$.excludedItems[0].reason").value("NOT_AGE_RELATED"));
    }

    @Test
    @DisplayName("쓸 수 있는 항목이 하나뿐이면 NOT_ENOUGH_ITEMS 이고 체력 나이는 비운다")
    void 항목이_모자라면() throws Exception {
        register(dadId, "{\"itemCode\":\"020\",\"value\":36},{\"itemCode\":\"012\",\"value\":10}");

        mvc.perform(get("/api/v1/profiles/" + dadId + "/fitness-age")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_ENOUGH_ITEMS"))
                .andExpect(jsonPath("$.fitnessAge").value(nullValue()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.excludedItems", hasSize(1)));
    }

    @Test
    @DisplayName("측정이 없으면 NO_TEST, 아이 프로필이면 AGE_OUT_OF_RANGE")
    void 측정이_없거나_성인이_아니면() throws Exception {
        mvc.perform(get("/api/v1/profiles/" + dadId + "/fitness-age")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_TEST"))
                .andExpect(jsonPath("$.testedOn").value(nullValue()))
                .andExpect(jsonPath("$.items", hasSize(0)));

        register(childId, "{\"itemCode\":\"020\",\"value\":42}");
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-age")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGE_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.age").value(11))
                .andExpect(jsonPath("$.fitnessAge").value(nullValue()));
    }

    @Test
    @DisplayName("아이 계정은 403 NOT_A_PARENT")
    void 아이_계정은_볼_수_없다() throws Exception {
        when(familyAccess.requireMember(kidUserId, familyId)).thenReturn(childAccountOf(UUID.randomUUID(), familyId));
        mvc.perform(get("/api/v1/profiles/" + dadId + "/fitness-age")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(kidUserId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
    }

    @Test
    void 토큰_없이_부르면_401() throws Exception {
        mvc.perform(get("/api/v1/profiles/" + dadId + "/fitness-age")).andExpect(status().isUnauthorized());
    }
}
