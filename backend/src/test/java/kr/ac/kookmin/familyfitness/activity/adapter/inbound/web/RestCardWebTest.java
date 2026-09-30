package kr.ac.kookmin.familyfitness.activity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.activity.application.port.RestCardRepository;
import kr.ac.kookmin.familyfitness.activity.domain.RestCard;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway(V137 rest_cards) 위에서 쉬는 날 카드 주소 세 개를 끝까지 돈다.
 * identity 는 목: 같은 가족 · 보호자 판단과 가족 구성원 목록만 흉내 낸다. 「오늘」 은 실제 KST 날짜다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestCardWebTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ActivityRecorder recorder;

    @Autowired
    RestCardRepository repository;

    @Autowired
    RestDayQuery restDays;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    CheerQuery cheerQuery;

    @MockitoBean
    AiGateway ai;

    private final UUID parentUser = UUID.randomUUID();
    private final UUID childUser = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private final YearMonth thisMonth = YearMonth.from(today);
    private UUID familyId;
    private UUID parentId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        parentId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        childId = rows.profile(familyId, LocalDate.of(2015, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        ProfileSummary parent = summary(parentId, "엄마", ProfileRole.PARENT);
        ProfileSummary child = summary(childId, "서준", ProfileRole.CHILD);
        when(familyAccess.requireMember(any(), any())).thenReturn(child);
        when(familyAccess.requireParent(parentUser, familyId)).thenReturn(parent);
        when(familyAccess.requireParent(childUser, familyId)).thenThrow(new NotAParentException());
        when(profileQuery.summariesOfFamily(familyId)).thenReturn(List.of(parent, child));
    }

    private ProfileSummary summary(UUID profileId, String name, ProfileRole role) {
        return new ProfileSummary(
                profileId,
                familyId,
                name,
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                role == ProfileRole.CHILD,
                true);
    }

    private String path() {
        return "/api/v1/families/" + familyId + "/rest-cards";
    }

    private ResultActions use(UUID userId, String body) throws Exception {
        return mvc.perform(post(path())
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions use(LocalDate date) throws Exception {
        return use(parentUser, "{\"date\":\"" + date + "\"}");
    }

    private ResultActions cancel(UUID userId, String date) throws Exception {
        return mvc.perform(delete(path() + "/" + date).header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)));
    }

    /** 규칙을 거치지 않고 카드 행을 넣는다(다른 보호자가 앞서 쓴 카드) */
    private void storeCard(LocalDate date, int cardNo) {
        jdbc.update(
                "insert into rest_cards (id, family_id, rest_date, rest_month, card_no, created_by, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                familyId,
                date,
                YearMonth.from(date).atDay(1),
                cardNo,
                parentId,
                Instant.now());
    }

    /** 이번 달에서 오늘이 아닌 날 두 개 */
    private List<LocalDate> twoOtherDaysThisMonth() {
        return List.of(thisMonth.atDay(1), thisMonth.atDay(2), thisMonth.atDay(3)).stream()
                .filter(day -> !day.equals(today))
                .limit(2)
                .toList();
    }

    @Test
    @DisplayName("보호자가 오늘을 쓰면 201 · 보면 같은 모양 · 되돌리면 200 으로 카드가 돌아온다")
    void 쓰고_보고_되돌린다() throws Exception {
        use(today)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.month").value(thisMonth.toString()))
                .andExpect(jsonPath("$.perMonth").value(2))
                .andExpect(jsonPath("$.left").value(1))
                .andExpect(jsonPath("$.days", contains(today.toString())));

        mvc.perform(get(path()).header(HttpHeaders.AUTHORIZATION, auth.bearer(childUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(thisMonth.toString()))
                .andExpect(jsonPath("$.left").value(1))
                .andExpect(jsonPath("$.days", contains(today.toString())));

        Map<String, Object> row = jdbc.queryForMap(
                "select card_no, created_by, rest_month from rest_cards where family_id = ?", familyId);
        assertThat(((Number) row.get("card_no")).intValue()).isEqualTo(1);
        assertThat(row.get("created_by")).isEqualTo(parentId);
        assertThat(row.get("rest_month").toString())
                .isEqualTo(thisMonth.atDay(1).toString());

        cancel(parentUser, today.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(thisMonth.toString()))
                .andExpect(jsonPath("$.left").value(2))
                .andExpect(jsonPath("$.days", empty()));
    }

    @Test
    @DisplayName("본문 칸 이름은 date — 설계안 이름 restDate 로 보내도 받는다")
    void 설계안_이름_restDate_도_받는다() throws Exception {
        use(parentUser, "{\"restDate\":\"" + today + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days", contains(today.toString())));
    }

    @Test
    @DisplayName("전환기 별칭 rest-days(FE 가 부르는 이름)도 같은 핸들러다 — 쓰기 · 보기 · 되돌리기 · 권한이 rest-cards 와 같다")
    void 별칭_rest_days_도_같은_핸들러다() throws Exception {
        String alias = "/api/v1/families/" + familyId + "/rest-days";

        mvc.perform(post(alias)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.month").value(thisMonth.toString()))
                .andExpect(jsonPath("$.perMonth").value(2))
                .andExpect(jsonPath("$.left").value(1))
                .andExpect(jsonPath("$.days", contains(today.toString())));
        String viaAlias = mvc.perform(get(alias)
                        .param("month", thisMonth.toString())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(childUser)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String viaContract = mvc.perform(get(path())
                        .param("month", thisMonth.toString())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(childUser)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(viaAlias).isEqualTo(viaContract);

        mvc.perform(post(alias)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(childUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        mvc.perform(delete(alias + "/" + today).header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.left").value(2))
                .andExpect(jsonPath("$.days", empty()));
        mvc.perform(get(alias)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("아이 계정은 쓰지도 되돌리지도 못한다 — 403 NOT_A_PARENT")
    void 아이는_쓰지도_되돌리지도_못한다() throws Exception {
        use(childUser, "{\"date\":\"" + today + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        cancel(childUser, today.toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
    }

    @Test
    @DisplayName("지난 날 · 다음 달 · 형식이 틀린 날 · 날짜 없음 · 본문 없음은 422 INVALID_DATE")
    void 쓸_수_없는_날은_422_INVALID_DATE() throws Exception {
        for (String body : List.of(
                "{\"date\":\"" + today.minusDays(1) + "\"}",
                "{\"date\":\"" + thisMonth.plusMonths(1).atDay(1) + "\"}",
                "{\"date\":\"2026/09/01\"}",
                "{}")) {
            use(parentUser, body)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
        }
        mvc.perform(post(path()).header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
    }

    @Test
    @DisplayName("이미 쉬는 날이면 409 ALREADY_REST_DAY")
    void 이미_쉬는_날이면_409() throws Exception {
        use(today).andExpect(status().isCreated());

        use(today)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REST_DAY"));
    }

    @Test
    @DisplayName("이번 달 두 장을 다 썼으면 409 NO_REST_CARD_LEFT")
    void 두_장을_다_썼으면_409() throws Exception {
        List<LocalDate> used = twoOtherDaysThisMonth();
        storeCard(used.get(0), 1);
        storeCard(used.get(1), 2);

        use(today)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_REST_CARD_LEFT"));
    }

    @Test
    @DisplayName("그날 운동한 아이가 있으면 422 ALREADY_MOVED")
    void 그날_운동한_아이가_있으면_422() throws Exception {
        recorder.addActiveMinutes(childId, today, ActivitySource.TIMER, 5);

        use(today)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MOVED"));
    }

    @Test
    @DisplayName("걸음수만 적은 날은 운동한 날로 보지 않는다")
    void 걸음수만_적은_날은_운동한_날이_아니다() throws Exception {
        recorder.overwriteSteps(childId, today, 3000);

        use(today).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("되돌리기 — 지난 날 422 INVALID_DATE · 쉬는 날이 아니면 404 NOT_REST_DAY · 형식이 틀리면 400")
    void 되돌릴_수_없는_경우() throws Exception {
        cancel(parentUser, today.minusDays(1).toString())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
        cancel(parentUser, today.toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_REST_DAY"));
        cancel(parentUser, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("month 를 주면 그달 카드를 준다 · 형식이 틀리면 400")
    void month_를_주면_그달_카드를_준다() throws Exception {
        YearMonth lastMonth = thisMonth.minusMonths(1);
        storeCard(lastMonth.atDay(5), 1);

        mvc.perform(get(path())
                        .param("month", lastMonth.toString())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(lastMonth.toString()))
                .andExpect(jsonPath("$.left").value(1))
                .andExpect(jsonPath("$.days", contains(lastMonth.atDay(5).toString())));
        mvc.perform(get(path()).param("month", "2026-9").header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 없이 부르면 401")
    void 토큰_없이_부르면_401() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("저장소 — 같은 날이 이미 있으면 insert 가 false(동시 요청에서 늦은 쪽)")
    void 같은_날이_이미_있으면_insert_가_false() {
        storeCard(today, 1);

        assertThat(repository.insert(RestCard.use(familyId, today, 2, parentId, Instant.now())))
                .isFalse();
    }

    @Test
    @DisplayName("저장소 — 같은 달 같은 카드 번호가 이미 있으면 insert 가 false(세 장째를 DB 가 막는다)")
    void 같은_카드_번호가_이미_있으면_insert_가_false() {
        List<LocalDate> days = twoOtherDaysThisMonth();
        storeCard(days.get(0), 1);

        assertThat(repository.insert(RestCard.use(familyId, days.get(1), 1, parentId, Instant.now())))
                .isFalse();
    }

    @Test
    @DisplayName("저장소 — 유니크가 아닌 제약 위반(없는 보호자 프로필 FK)은 false 로 삼키지 않고 그대로 던진다")
    void 유니크가_아닌_위반은_그대로_던진다() {
        RestCard orphan = RestCard.use(familyId, today, 1, UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> repository.insert(orphan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RestDayQuery 는 달을 넘는 범위도 오름차순으로 준다")
    void RestDayQuery는_달을_넘는_범위도_준다() {
        YearMonth lastMonth = thisMonth.minusMonths(1);
        storeCard(thisMonth.atDay(2), 1);
        storeCard(lastMonth.atEndOfMonth(), 1);
        storeCard(lastMonth.atDay(1), 2);

        assertThat(restDays.restDaysBetween(familyId, lastMonth.atDay(20), thisMonth.atDay(3)))
                .containsExactly(lastMonth.atEndOfMonth(), thisMonth.atDay(2));
    }

    @Test
    @DisplayName("RestDayQuery 는 여러 가족의 쉬는 날을 한 번에 준다 — 가족마다 오름차순, 쉬는 날이 없는 가족은 빈 목록")
    void RestDayQuery는_여러_가족을_한_번에_준다() {
        UUID neighbor = rows.family("이웃");
        UUID neighborParent = rows.profile(neighbor, LocalDate.of(1985, 1, 1), Sex.M, ProfileRole.PARENT, "아빠");
        UUID quiet = rows.family("쉬는 날 없는 집");
        YearMonth lastMonth = thisMonth.minusMonths(1);
        storeCard(thisMonth.atDay(2), 1);
        storeCard(lastMonth.atDay(28), 1);
        jdbc.update(
                "insert into rest_cards (id, family_id, rest_date, rest_month, card_no, created_by, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                neighbor,
                thisMonth.atDay(1),
                thisMonth.atDay(1),
                1,
                neighborParent,
                Instant.now());

        Map<UUID, List<LocalDate>> days = restDays.restDaysOfFamilies(
                List.of(familyId, neighbor, quiet), lastMonth.atDay(20), thisMonth.atDay(3));

        assertThat(days).containsOnlyKeys(familyId, neighbor, quiet);
        assertThat(days.get(familyId))
                .containsExactlyElementsOf(restDays.restDaysBetween(familyId, lastMonth.atDay(20), thisMonth.atDay(3)))
                .containsExactly(lastMonth.atDay(28), thisMonth.atDay(2));
        assertThat(days.get(neighbor)).containsExactly(thisMonth.atDay(1));
        assertThat(days.get(quiet)).isEmpty();
    }
}
