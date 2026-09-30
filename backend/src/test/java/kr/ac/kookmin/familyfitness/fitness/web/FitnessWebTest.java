package kr.ac.kookmin.familyfitness.fitness.web;

import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.childAccountOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.detailsOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.parentOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.summaryOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException;
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryResponse;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway(V1~V3, 국민체력100 규준 2024-07~2026-07) 위에서 fitness 웹 어댑터를 끝까지 돈다.
 * identity·AI 는 목: 같은 가족 판단과 프로필 상세, 예측 응답을 흉내 낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FitnessWebTest {
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

    @MockitoBean
    AiGateway ai;

    /** 보호자 계정 */
    private final UUID userId = UUID.randomUUID();

    /** 초대코드로 붙은 자녀(CHILD) 본인 계정 */
    private final UUID kidUserId = UUID.randomUUID();

    private UUID familyId;
    private UUID childId;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private final LocalDate testedOn = today.minusDays(1);

    /** 항상 만 11세(유소년, 실측 규준이 있는 나이)가 되도록 생년월일을 오늘 기준으로 잡는다. */
    private final LocalDate birthDate = today.minusYears(11).minusMonths(4);

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        childId = rows.profile(familyId, birthDate, Sex.F);
        when(familyAccess.requireSameFamilyAsProfile(userId, childId))
                .thenReturn(summaryOf(childId, familyId, AgeGroup.YOUTH));
        // 예측은 대신할 수 있는 프로필만(계정 없는 아이를 보호자가 대신) — requireActingAs
        when(familyAccess.requireActingAs(userId, childId)).thenReturn(summaryOf(childId, familyId, AgeGroup.YOUTH));
        when(familyAccess.requireParentOfProfile(userId, childId)).thenReturn(parentOf(UUID.randomUUID(), familyId));
        when(familyAccess.requireMember(userId, familyId)).thenReturn(parentOf(UUID.randomUUID(), familyId));
        when(profileQuery.findDetails(childId))
                .thenReturn(detailsOf(childId, familyId, birthDate, Sex.F, null, null, true));

        when(familyAccess.requireSameFamilyAsProfile(kidUserId, childId))
                .thenReturn(summaryOf(childId, familyId, AgeGroup.YOUTH));
        when(familyAccess.requireParentOfProfile(kidUserId, childId)).thenThrow(new NotAParentException());
        when(familyAccess.requireMember(kidUserId, familyId)).thenReturn(childAccountOf(UUID.randomUUID(), familyId));
    }

    private String bearer() {
        return auth.bearer(userId);
    }

    private record Item(String code, String value) {}

    private String registerBody(Item... items) {
        return registerBody(testedOn, "135.5", "31.2", items);
    }

    /** heightCm · weightKg 는 JSON 값 그대로 넣는다("null" 이면 그 회차에 안 적은 것). */
    private String registerBody(LocalDate on, String heightCm, String weightKg, Item... items) {
        String rendered = Stream.of(items)
                .map(it -> "{\"itemCode\":\"" + it.code() + "\",\"value\":" + it.value() + "}")
                .collect(Collectors.joining(","));
        return "{\"testedOn\":\"" + on + "\",\"source\":\"SELF_INPUT\",\"heightCm\":" + heightCm + ",\"weightKg\":"
                + weightKg + ",\n" + " \"items\":[" + rendered + "]}";
    }

    private ResultActions register(LocalDate on, String heightCm, String weightKg, Item... items) throws Exception {
        return mvc.perform(post("/api/v1/profiles/" + childId + "/fitness-tests")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(on, heightCm, weightKg, items)));
    }

    private ResultActions registerYouthTest() throws Exception {
        return register(
                testedOn,
                "135.5",
                "31.2",
                new Item("012", "9"),
                new Item("020", "42"),
                new Item("022", "142"),
                new Item("028", "30"));
    }

    @Test
    @DisplayName("토큰 없이 부르면 401")
    void 토큰_없이_부르면_401() throws Exception {
        mvc.perform(get("/api/v1/fitness/items").param("ageGroup", "유소년"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests/latest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유소년 측정 항목 목록")
    void 유소년_측정_항목_목록() throws Exception {
        mvc.perform(get("/api/v1/fitness/items")
                        .param("ageGroup", "유소년")
                        .param("sex", "F")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageGroup").value("유소년"))
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(
                        jsonPath("$.items[*].itemCode", containsInAnyOrder("009", "012", "028", "020", "022", "043")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='020')].itemLabel", contains("15m 왕복오래달리기")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='020')].factor", contains("심폐지구력")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='020')].inputGroup", contains("EQUIPMENT")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='020')].optional", contains(true)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='028')].equipment", contains("악력계")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].optional", contains(false)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].equipment", everyItem(nullValue())))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].range.min", contains(-30)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].higherIsBetter", contains(true)));
    }

    @Test
    @DisplayName("모르는 연령대는 400")
    void 모르는_연령대는_400() throws Exception {
        mvc.perform(get("/api/v1/fitness/items").param("ageGroup", "노년").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("유소년 여아 11세 측정을 등록하면 국민체력100 규준으로 백분위가 붙는다")
    void 유소년_여아_11세_측정을_등록하면_국민체력100_규준으로_백분위가_붙는다() throws Exception {
        registerYouthTest()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fitnessTestId").isNotEmpty())
                .andExpect(jsonPath("$.testedOn").value(testedOn.toString()))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].percentile", contains(48)))
                // 등급 기준 85·65·40 — 백분위 48 은 3등급(옛 기준 90·75·50 이면 참가)
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].grade", contains("3등급")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].band", contains("steady")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].topPercentText", contains("상위 52%")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='020')].percentile", contains(35)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='020')].itemLabel", contains("15m 왕복오래달리기")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='022')].percentile", contains(63)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='022')].band", contains("steady")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='028')].percentile", contains(10)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='028')].unit", contains("%")))
                .andExpect(jsonPath("$.weakest.itemCode").value("028"))
                .andExpect(jsonPath("$.weakest.factor").value("근력"))
                .andExpect(jsonPath("$.weakest.percentile").value(10))
                .andExpect(jsonPath("$.strongest.itemCode").value("022"))
                .andExpect(jsonPath("$.strongest.percentile").value(63))
                .andExpect(jsonPath("$.disclaimer").value(Copy.FITNESS_DISCLAIMER));
    }

    @Test
    @DisplayName("등록 오류 코드 — 혈압·항목 없음·중복 날짜·연령대 밖 항목·동의")
    void 등록_오류_코드_혈압_항목_없음_중복_날짜_연령대_밖_항목_동의() throws Exception {
        mvc.perform(post("/api/v1/profiles/" + childId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(new Item("028", "30"), new Item("005", "80"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_ALLOWED"));

        mvc.perform(post("/api/v1/profiles/" + childId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NO_ITEMS"));

        mvc.perform(post("/api/v1/profiles/" + childId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(new Item("013", "20"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOR_AGE_GROUP"));

        registerYouthTest().andExpect(status().isCreated());
        registerYouthTest()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_DATE"));

        when(familyAccess.requireSameFamilyAsProfile(userId, childId))
                .thenReturn(summaryOf(childId, familyId, AgeGroup.YOUTH, false, true, false));
        mvc.perform(post("/api/v1/profiles/" + childId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(new Item("028", "30"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    @DisplayName("요청 형식이 틀리면 400 BAD_REQUEST")
    void 요청_형식이_틀리면_400_BAD_REQUEST() throws Exception {
        mvc.perform(
                        post("/api/v1/profiles/" + childId + "/fitness-tests")
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"testedOn\":\"" + testedOn
                                                + "\",\"source\":\"SELF_INPUT\",\"heightCm\":10,\"items\":[{\"itemCode\":\"028\",\"value\":30}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("다른 가족의 프로필이면 403")
    void 다른_가족의_프로필이면_403() throws Exception {
        UUID stranger = UUID.randomUUID();
        when(familyAccess.requireSameFamilyAsProfile(stranger, childId)).thenThrow(new NotSameFamilyException());
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests/latest")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
    }

    @Test
    @DisplayName("이력이 없으면 latest 는 200 에 null 과 빈 레이더")
    void 이력이_없으면_latest_는_200_에_null_과_빈_레이더() throws Exception {
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitnessTestId").value(nullValue()))
                .andExpect(jsonPath("$.testedOn").value(nullValue()))
                .andExpect(jsonPath("$.heightCm").value(nullValue()))
                .andExpect(jsonPath("$.weightKg").value(nullValue()))
                .andExpect(jsonPath("$.radar", hasSize(6)))
                .andExpect(jsonPath("$.radar[0].factor").value("근력"))
                .andExpect(jsonPath("$.radar[0].percentile").value(nullValue()))
                .andExpect(jsonPath("$.radar[4].factor").value("순발력"))
                .andExpect(jsonPath("$.radar[5].factor").value("민첩성"))
                .andExpect(jsonPath("$.radar[5].percentile").value(nullValue()))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.weakest").value(nullValue()))
                .andExpect(jsonPath("$.strongest").value(nullValue()))
                .andExpect(jsonPath("$.coachDirection").value("GROWTH"))
                .andExpect(jsonPath("$.disclaimer").value(Copy.FITNESS_DISCLAIMER));
    }

    @Test
    @DisplayName("latest 는 그 회차의 키·몸무게, 레이더 6요인, 코치 방향을 준다")
    void latest_는_그_회차의_키_몸무게_레이더_6요인_코치_방향을_준다() throws Exception {
        registerYouthTest().andExpect(status().isCreated());

        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitnessTestId").isNotEmpty())
                .andExpect(jsonPath("$.testedOn").value(testedOn.toString()))
                .andExpect(jsonPath("$.heightCm").value(135.5))
                .andExpect(jsonPath("$.weightKg").value(31.2))
                .andExpect(jsonPath("$.radar[*].factor", contains("근력", "근지구력", "유연성", "심폐지구력", "순발력", "민첩성")))
                .andExpect(jsonPath("$.radar[0].percentile").value(10))
                .andExpect(jsonPath("$.radar[1].percentile").value(nullValue()))
                .andExpect(jsonPath("$.radar[2].percentile").value(48))
                .andExpect(jsonPath("$.radar[3].percentile").value(35))
                .andExpect(jsonPath("$.radar[4].percentile").value(63))
                .andExpect(jsonPath("$.radar[5].percentile").value(nullValue()))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[?(@.itemCode=='028')].grade", contains("참가")))
                .andExpect(jsonPath("$.weakest.itemCode").value("028"))
                .andExpect(jsonPath("$.strongest.itemCode").value("022"))
                .andExpect(jsonPath("$.coachDirection").value("GROWTH"));
    }

    @Test
    @DisplayName("043 반복옆뛰기를 재면 레이더 민첩성 꼭지점에 백분위가 들어간다")
    void 반복옆뛰기_043_을_재면_레이더_민첩성_꼭지점에_백분위가_들어간다() throws Exception {
        // 여아 만 11세 규준에서 35회는 85번째 백분위 — 새 기준 1등급(옛 기준이면 2등급)
        register(testedOn, "135.5", "31.2", new Item("043", "35"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].percentile").value(85))
                .andExpect(jsonPath("$.items[0].grade").value("1등급"));

        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radar[5].factor").value("민첩성"))
                .andExpect(jsonPath("$.radar[5].percentile").value(85))
                .andExpect(jsonPath("$.radar[0].percentile").value(nullValue()));
    }

    @Test
    @DisplayName("측정 이력은 최근 회차부터 id·날짜·통합 백분위·그 회차의 키·몸무게를 주고, 없으면 빈 목록")
    void 측정_이력은_최근_회차부터_id_날짜_통합_백분위_그_회차의_키_몸무게를_주고_없으면_빈_목록() throws Exception {
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests", hasSize(0)));

        LocalDate earlier = testedOn.minusDays(30);
        register(earlier, "null", "null", new Item("028", "30")).andExpect(status().isCreated());
        registerYouthTest().andExpect(status().isCreated());

        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests")
                        .param("size", "12")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests", hasSize(2)))
                .andExpect(jsonPath("$.tests[*].testedOn", contains(testedOn.toString(), earlier.toString())))
                .andExpect(jsonPath("$.tests[0].fitnessTestId").isNotEmpty())
                // 체력 지도 latest.overallPercentile 과 같은 셈(항목 백분위 평균)
                .andExpect(jsonPath("$.tests[0].overallPercentile").value(39))
                .andExpect(jsonPath("$.tests[0].heightCm").value(135.5))
                .andExpect(jsonPath("$.tests[0].weightKg").value(31.2))
                .andExpect(jsonPath("$.tests[1].overallPercentile").value(10))
                .andExpect(jsonPath("$.tests[1].heightCm").value(nullValue()))
                .andExpect(jsonPath("$.tests[1].weightKg").value(nullValue()));

        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests", hasSize(1)))
                .andExpect(jsonPath("$.tests[0].testedOn").value(testedOn.toString()));
    }

    @Test
    @DisplayName("측정 이력 — size 가 1~100 밖이면 400, 다른 가족이면 403")
    void 측정_이력_size_가_1_100_밖이면_400_다른_가족이면_403() throws Exception {
        for (String size : List.of("0", "101")) {
            mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests")
                            .param("size", size)
                            .header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }

        UUID stranger = UUID.randomUUID();
        when(familyAccess.requireSameFamilyAsProfile(stranger, childId)).thenThrow(new NotSameFamilyException());
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
    }

    @Test
    @DisplayName("가족 체력 지도는 구성원 카드에 한 줄 요약과 최신 측정 요약을 싣고, 미측정 구성원은 null 로 둔다")
    void 가족_체력_지도는_구성원_카드에_한_줄_요약과_최신_측정_요약을_싣고_미측정_구성원은_null_로_둔다() throws Exception {
        UUID parentId = UUID.randomUUID();
        ProfileSummary child = summaryOf(childId, familyId, AgeGroup.YOUTH);
        ProfileSummary parentBase = summaryOf(parentId, familyId, AgeGroup.ADULT);
        ProfileSummary parent = new ProfileSummary(
                parentBase.profileId(),
                parentBase.familyId(),
                parentBase.name(),
                ProfileRole.PARENT,
                parentBase.ageGroup(),
                Sex.M,
                parentBase.hasAccount(),
                parentBase.inviteStatus(),
                parentBase.supportMode(),
                parentBase.measurable(),
                parentBase.consentRequired(),
                parentBase.consentGiven());
        when(familyAccess.requireMember(userId, familyId)).thenReturn(parent);
        when(profileQuery.summariesOfFamily(familyId)).thenReturn(List.of(parent, child));
        when(profileQuery.familyName(familyId)).thenReturn("데모네");
        registerYouthTest().andExpect(status().isCreated());

        mvc.perform(get("/api/v1/families/" + familyId + "/fitness-map").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyName").value("데모네"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].profileId").value(parentId.toString()))
                .andExpect(jsonPath("$.members[0].sex").value("M"))
                .andExpect(jsonPath("$.members[1].sex").value("F"))
                .andExpect(jsonPath("$.members[0].headline").value(nullValue()))
                .andExpect(jsonPath("$.members[0].latest").value(nullValue()))
                .andExpect(jsonPath("$.members[1].headline").value("유소년 상위 61%"))
                .andExpect(jsonPath("$.members[1].latest.overallPercentile").value(39))
                .andExpect(jsonPath("$.members[1].latest.weakest.itemCode").value("028"))
                .andExpect(jsonPath("$.members[1].latest.coachDirection").value("GROWTH"))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("체력 지도 headline 은 오늘 연령대가 아니라 측정 당시 연령대로 붙는다")
    void 체력_지도_headline_은_오늘_연령대가_아니라_측정_당시_연령대로_붙는다() throws Exception {
        registerYouthTest().andExpect(status().isCreated());
        // 만 11세(유소년)에 잰 뒤 13세 생일이 지나 오늘 연령대가 청소년이 된 경우
        ProfileSummary grownUp = summaryOf(childId, familyId, AgeGroup.ADOLESCENT);
        when(profileQuery.summariesOfFamily(familyId)).thenReturn(List.of(grownUp));

        mvc.perform(get("/api/v1/families/" + familyId + "/fitness-map").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].ageGroup").value("청소년"))
                .andExpect(jsonPath("$.members[0].headline").value("유소년 상위 61%"));
    }

    @Test
    @DisplayName("측정일을 주면 항목표는 그날의 연령대다 — 13세 생일 며칠 전 결과지는 유소년 항목, 오늘은 청소년 항목")
    void 측정일을_주면_항목표는_그날의_연령대다() throws Exception {
        // 사흘 전에 만 13세가 됐다
        LocalDate turned13 = today.minusYears(13).minusDays(3);
        when(profileQuery.findDetails(childId))
                .thenReturn(detailsOf(childId, familyId, turned13, Sex.M, null, null, true));

        mvc.perform(get("/api/v1/fitness/items")
                        .param("profileId", childId.toString())
                        .param("testedOn", today.minusDays(5).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageGroup").value("유소년"))
                .andExpect(
                        jsonPath("$.items[*].itemCode", containsInAnyOrder("009", "012", "028", "020", "022", "043")))
                .andExpect(jsonPath("$.items[?(@.itemCode=='020')].itemLabel", contains("15m 왕복오래달리기")));

        // testedOn 이 없으면 오늘. ageGroup 을 같이 보내도 profileId 가 이긴다
        mvc.perform(get("/api/v1/fitness/items")
                        .param("profileId", childId.toString())
                        .param("ageGroup", "유소년")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageGroup").value("청소년"))
                .andExpect(jsonPath("$.items[?(@.itemCode=='010')].itemCode", contains("010")));
    }

    @Test
    @DisplayName("항목표 오류 — testedOn 만 있거나 아무것도 없으면 400, 미래 날짜 400, 자녀 계정 403 NOT_A_PARENT")
    void 항목표_오류() throws Exception {
        mvc.perform(get("/api/v1/fitness/items")
                        .param("testedOn", testedOn.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/fitness/items").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/fitness/items")
                        .param("profileId", childId.toString())
                        .param("testedOn", today.plusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/fitness/items")
                        .param("profileId", childId.toString())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(kidUserId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
    }

    @Test
    @DisplayName("항목 범위 밖 값은 400 ITEM_OUT_OF_RANGE 로 거절하고 저장하지 않는다")
    void 항목_범위_밖_값은_400_ITEM_OUT_OF_RANGE() throws Exception {
        // 012 범위는 -30~40 (GET /fitness/items 가 알려 준 값)
        register(testedOn, "135.5", "31.2", new Item("012", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ITEM_OUT_OF_RANGE"));
        register(testedOn, "135.5", "31.2", new Item("012", "40"), new Item("028", "30"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("자녀 계정은 자기 것이든 남의 것이든 측정을 등록하지 못한다(403 NOT_A_PARENT)")
    void 자녀_계정은_측정을_등록하지_못한다() throws Exception {
        mvc.perform(post("/api/v1/profiles/" + childId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(kidUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(new Item("028", "30"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));

        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests", hasSize(0)));
    }

    @Test
    @DisplayName("자녀 계정의 latest 는 부모만 볼 값(몸무게 · 백분위 · 등급 · 약한 항목 · 코치 방향)을 비우고 잰 값과 키는 준다")
    void 자녀_계정의_latest_는_부모만_볼_값을_비운다() throws Exception {
        registerYouthTest().andExpect(status().isCreated());

        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests/latest")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(kidUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitnessTestId").isNotEmpty())
                .andExpect(jsonPath("$.testedOn").value(testedOn.toString()))
                .andExpect(jsonPath("$.heightCm").value(135.5))
                .andExpect(jsonPath("$.weightKg").value(nullValue()))
                .andExpect(jsonPath("$.radar", hasSize(6)))
                .andExpect(jsonPath("$.radar[*].percentile", everyItem(nullValue())))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].value").isNumber())
                .andExpect(jsonPath("$.items[*].percentile", everyItem(nullValue())))
                .andExpect(jsonPath("$.items[*].grade", everyItem(nullValue())))
                .andExpect(jsonPath("$.items[*].band", everyItem(nullValue())))
                .andExpect(jsonPath("$.items[*].topPercentText", everyItem(nullValue())))
                .andExpect(jsonPath("$.weakest").value(nullValue()))
                .andExpect(jsonPath("$.strongest").value(nullValue()))
                .andExpect(jsonPath("$.coachDirection").value(nullValue()))
                .andExpect(jsonPath("$.disclaimer").value(Copy.FITNESS_DISCLAIMER));

        // 같은 회차를 부모 계정으로 부르면 그대로다
        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(31.2))
                .andExpect(jsonPath("$.items[?(@.itemCode=='012')].percentile", contains(48)))
                .andExpect(jsonPath("$.weakest.itemCode").value("028"))
                .andExpect(jsonPath("$.coachDirection").value("GROWTH"));
    }

    @Test
    @DisplayName("자녀 계정의 측정 이력은 몸무게만 비우고 통합 백분위 · 키는 준다")
    void 자녀_계정의_측정_이력은_몸무게만_비운다() throws Exception {
        registerYouthTest().andExpect(status().isCreated());

        mvc.perform(get("/api/v1/profiles/" + childId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(kidUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests", hasSize(1)))
                .andExpect(jsonPath("$.tests[0].overallPercentile").value(39))
                .andExpect(jsonPath("$.tests[0].heightCm").value(135.5))
                .andExpect(jsonPath("$.tests[0].weightKg").value(nullValue()));
    }

    @Test
    @DisplayName("자녀 계정의 체력 지도는 구성원마다 overallPercentile 만 남기고 headline · 약한 항목 · 코치 방향을 비운다")
    void 자녀_계정의_체력_지도는_overallPercentile_만_남긴다() throws Exception {
        when(profileQuery.summariesOfFamily(familyId))
                .thenReturn(List.of(summaryOf(childId, familyId, AgeGroup.YOUTH)));
        registerYouthTest().andExpect(status().isCreated());

        mvc.perform(get("/api/v1/families/" + familyId + "/fitness-map")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(kidUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].profileId").value(childId.toString()))
                .andExpect(jsonPath("$.members[0].headline").value(nullValue()))
                .andExpect(jsonPath("$.members[0].latest.testedOn").value(testedOn.toString()))
                .andExpect(jsonPath("$.members[0].latest.overallPercentile").value(39))
                .andExpect(jsonPath("$.members[0].latest.weakest").value(nullValue()))
                .andExpect(jsonPath("$.members[0].latest.strongest").value(nullValue()))
                .andExpect(jsonPath("$.members[0].latest.coachDirection").value(nullValue()));
    }

    @Test
    @DisplayName("예측은 AI 응답을 MAINTAIN 포인트로 저장해 201 로 돌려준다")
    void 예측은_AI_응답을_MAINTAIN_포인트로_저장해_201_로_돌려준다() throws Exception {
        registerYouthTest().andExpect(status().isCreated());
        when(ai.trajectory(any()))
                .thenReturn(new TrajectoryResponse(
                        "cross_sectional_group_distribution",
                        "028",
                        "상대악력",
                        "%",
                        List.of(
                                new TrajectoryResponse.Band(11, 28.0, 36.0, 44.0, 120),
                                new TrajectoryResponse.Band(14, 32.0, 40.0, 50.0, 110),
                                new TrajectoryResponse.Band(21, 45.0, 60.0, 75.0, 90)),
                        Copy.TRAJECTORY_NOTICE,
                        false));

        mvc.perform(post("/api/v1/profiles/" + childId + "/predictions")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonYears\":10,\"itemCode\":\"028\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.predictionId").isNotEmpty())
                .andExpect(jsonPath("$.modelVersion").value("ai-trajectory-v1"))
                .andExpect(jsonPath("$.basis").value("cross_sectional_group_distribution"))
                .andExpect(jsonPath("$.notice").value(Copy.TRAJECTORY_NOTICE))
                .andExpect(jsonPath("$.points", hasSize(3)))
                .andExpect(jsonPath("$.points[*].scenario", contains("MAINTAIN", "MAINTAIN", "MAINTAIN")))
                .andExpect(jsonPath("$.points[*].yearsFromNow", contains(0, 3, 10)))
                .andExpect(jsonPath("$.points[1].itemCode").value("028"))
                .andExpect(jsonPath("$.points[1].p10").value(32.0))
                .andExpect(jsonPath("$.points[1].p50").value(40.0))
                .andExpect(jsonPath("$.points[1].p90").value(50.0));
    }

    @Test
    @DisplayName("예측 오류 — 측정 없음 422, AI 장애 503")
    void 예측_오류_측정_없음_422_AI_장애_503() throws Exception {
        mvc.perform(post("/api/v1/profiles/" + childId + "/predictions").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("NO_FITNESS_TEST"));

        registerYouthTest().andExpect(status().isCreated());
        when(ai.trajectory(any())).thenThrow(new AiUnavailableException("AI 서비스 응답 없음"));
        mvc.perform(post("/api/v1/profiles/" + childId + "/predictions").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("TEMPORARILY_UNAVAILABLE"));
    }
}
