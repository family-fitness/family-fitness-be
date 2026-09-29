package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway(V132 유튜브 클립 적재 · V141 구간 찜 · V161 공단 영상 클립 적재) 위에서 운동 구간 목록과 찜 주소를 끝까지 돈다.
 * 수는 AI 커밋 2af9002(유튜브) · 610959a(공단) 판 기준이다. 목록은 유튜브 구간과 공단 영상을 번갈아 세운다(ExerciseService.alternateSources).
 * identity 는 목: 같은 가족 판단과 계정의 자기 프로필만 흉내 낸다. 찜 행의 FK 때문에 프로필 행은 실제로 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExerciseWebTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    CheerQuery cheerQuery;

    @MockitoBean
    AiGateway ai;

    private final UUID parentUser = UUID.randomUUID();
    private final UUID outsiderUser = UUID.randomUUID();
    private UUID familyId;
    private UUID parentId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        parentId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        childId = rows.profile(familyId, LocalDate.of(2015, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        ProfileSummary parent = summary(parentId, "엄마", ProfileRole.PARENT, AgeGroup.ADULT);
        ProfileSummary child = summary(childId, "서준", ProfileRole.CHILD, AgeGroup.YOUTH);
        when(familyAccess.requireSameFamilyAsProfile(parentUser, parentId)).thenReturn(parent);
        when(familyAccess.requireSameFamilyAsProfile(parentUser, childId)).thenReturn(child);
        when(familyAccess.requireSameFamilyAsProfile(outsiderUser, childId)).thenThrow(new NotSameFamilyException());
        when(profileQuery.summariesOfUser(parentUser)).thenReturn(List.of(parent));
    }

    private ProfileSummary summary(UUID profileId, String name, ProfileRole role, AgeGroup ageGroup) {
        return new ProfileSummary(
                profileId,
                familyId,
                name,
                role,
                ageGroup,
                Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                role == ProfileRole.CHILD,
                true);
    }

    /** @param params 이름 · 값을 번갈아 */
    private ResultActions list(UUID userId, String... params) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, auth.bearer(userId));
        for (int i = 0; i < params.length; i += 2) request = request.param(params[i], params[i + 1]);
        return mvc.perform(request);
    }

    private ResultActions favorite(UUID userId, String exerciseId, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/exercises/" + exerciseId + "/favorite")
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return mvc.perform(request);
    }

    private String body(UUID profileId, boolean favorited) {
        return "{\"profileId\":\"" + profileId + "\",\"favorited\":" + favorited + "}";
    }

    private int favoriteRows() {
        Integer n = jdbc.queryForObject(
                "select count(*) from exercise_favorites where profile_id = ?", Integer.class, childId);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("보는 아이의 연령대(유소년) 구간을 같은 제목 하나씩 앞 40개까지 FE 모양으로 준다 — 공단 영상은 한 편이 구간 하나다")
    void 보는_아이의_연령대_구간을_같은_제목_하나씩_앞_40개까지_FE_모양으로_준다() throws Exception {
        // 유튜브 구간과 공단 영상 구간을 같은 제목 하나씩 모아 160개(공단 104 · 유튜브 56). 유튜브 · 공단을 번갈아 세운다
        list(parentUser, "profileId", childId.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(160))
                .andExpect(jsonPath("$.clips", hasSize(40)))
                .andExpect(jsonPath("$.clips[0].clipId").value("Eg3GpTv7z8s-102"))
                .andExpect(jsonPath("$.clips[1].clipId").value("0AUDLJ08S_00351-0"))
                .andExpect(jsonPath("$.clips[1].videoId").value("0AUDLJ08S_00351"))
                .andExpect(jsonPath("$.clips[1].startSec").value(0))
                .andExpect(jsonPath("$.clips[1].endSec").value(91))
                .andExpect(jsonPath("$.clips[1].title").value("팔굽혀펴기"))
                .andExpect(jsonPath("$.clips[1].factor").value("근력"))
                .andExpect(jsonPath("$.clips[1].phase").value("MAIN"))
                .andExpect(jsonPath("$.clips[1].homeOk").value(true))
                .andExpect(jsonPath("$.clips[1].quiet").value(true))
                .andExpect(jsonPath("$.clips[1].props").value(true))
                .andExpect(jsonPath("$.clips[1].favorited").value(false))
                .andExpect(jsonPath("$.clips[1].mediaUrl")
                        .value("https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00351.mp4"))
                .andExpect(jsonPath("$.clips[1].thumbnailUrl")
                        .value("https://openapi.kspo.or.kr/web/image/0AUDLJ08S_00351/0AUDLJ08S_00351_SC_00002.jpeg"));
        // 유튜브 구간은 지금 모양 그대로이고 mediaUrl · thumbnailUrl 이 null 이다
        list(parentUser, "profileId", childId.toString(), "q", "옆으로 누워 발 뒤로 넘기기")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.clips[0].clipId").value("Eg3GpTv7z8s-102"))
                .andExpect(jsonPath("$.clips[0].videoId").value("Eg3GpTv7z8s"))
                .andExpect(jsonPath("$.clips[0].startSec").value(102))
                .andExpect(jsonPath("$.clips[0].endSec").value(140))
                .andExpect(jsonPath("$.clips[0].factor").value("유연성"))
                .andExpect(jsonPath("$.clips[0].phase").value("WARMUP"))
                .andExpect(jsonPath("$.clips[0].props").value(false))
                .andExpect(jsonPath("$.clips[0].mediaUrl", nullValue()))
                .andExpect(jsonPath("$.clips[0].thumbnailUrl", nullValue()));
    }

    @Test
    @DisplayName("첫 쪽 40개에 유튜브 구간과 공단 영상이 번갈아 함께 선다 — 영상 id 차례로 세우면 공단 영상(0AUDLJ08S_…)만 앞에 섰다")
    void 첫_쪽에_두_출처가_번갈아_선다() throws Exception {
        for (ResultActions page : List.of(list(parentUser, "profileId", childId.toString()), list(parentUser))) {
            page.andExpect(status().isOk())
                    .andExpect(jsonPath("$.clips", hasSize(40)))
                    .andExpect(jsonPath("$.clips[0].mediaUrl", nullValue()))
                    .andExpect(jsonPath("$.clips[1].mediaUrl").isString())
                    .andExpect(jsonPath("$.clips[38].mediaUrl", nullValue()))
                    .andExpect(jsonPath("$.clips[39].mediaUrl").isString());
        }
    }

    @Test
    @DisplayName("공단 영상 이름에 제목 끝 「-1」 「-2」 가 붙어 나가지 않고, 「목 스트레칭」 여러 편은 한 이름으로 하나만 선다")
    void 공단_영상_이름에_제목_끝_번호가_붙어_나가지_않는다() throws Exception {
        list(parentUser, "q", "스트레칭")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clips[*].title", everyItem(not(matchesRegex(".*[-－]\\s*\\d+\\s*$")))))
                .andExpect(jsonPath("$.clips[?(@.title == '목 스트레칭')]", hasSize(1)));
    }

    @Test
    @DisplayName("어르신이 보면 첫 쪽의 공단 영상이 모두 어르신 영상이다 — 영상 id 차례로 세우면 성인(공통) 영상이 앞서 어르신 영상이 하나도 없었다")
    void 어르신이_보면_첫_쪽의_공단_영상이_어르신_영상이다() throws Exception {
        UUID grandpaId = rows.profile(familyId, LocalDate.of(1956, 4, 1), Sex.M, ProfileRole.PARENT, "할아버지");
        when(familyAccess.requireSameFamilyAsProfile(parentUser, grandpaId))
                .thenReturn(summary(grandpaId, "할아버지", ProfileRole.PARENT, AgeGroup.SENIOR));

        String body = list(parentUser, "profileId", grandpaId.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clips", hasSize(40)))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<String> kspo = com.jayway.jsonpath.JsonPath.read(body, "$.clips[?(@.mediaUrl != null)].clipId");

        assertThat(kspo).hasSize(20);
        assertThat(kspo).allSatisfy(clipId -> assertThat(jdbc.queryForObject(
                        "select age_group from video_exercises where clip_id = ?", String.class, clipId))
                .isEqualTo("SENIOR"));
        // 성인 영상도 그대로 함께 나온다(어르신 전용만 따로 두지 않는다) — 어르신 영상 뒤에 선다
        list(parentUser, "profileId", grandpaId.toString(), "q", "빠르게 걷기")
                .andExpect(jsonPath("$.clips[*].clipId", hasItem("0AUDLJ08S_00182-0")));
    }

    @Test
    @DisplayName("오십견 · 요통 같은 질환용 공단 영상은 어른 운동 찾기에 나오지 않는다 — 「막대 잡고 팔 안쪽?바깥 돌림」 이 나왔다")
    void 질환용_공단_영상은_운동_찾기에_나오지_않는다() throws Exception {
        list(parentUser, "q", "막대 잡고")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("profileId 가 없으면 호출한 계정의 자기 프로필(성인) 연령대로 거르고, 프로필이 없는 계정은 빈 목록이다")
    void profileId_가_없으면_호출한_계정의_자기_프로필_연령대로_거르고_프로필이_없는_계정은_빈_목록이다() throws Exception {
        list(parentUser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(217))
                .andExpect(jsonPath("$.clips[0].clipId").value("IhShIA-WJNE-20"))
                .andExpect(jsonPath("$.clips[1].clipId").value("0AUDLJ08S_00173-0"));
        list(outsiderUser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.clips", hasSize(0)));
    }

    @Test
    @DisplayName("요인은 한글 이름으로, 검색어는 앞뒤 공백을 빼고 제목의 부분 일치로 거른다")
    void 요인은_한글_이름으로_검색어는_앞뒤_공백을_빼고_제목의_부분_일치로_거른다() throws Exception {
        list(parentUser, "profileId", childId.toString(), "factor", "순발력")
                .andExpect(jsonPath("$.total").value(12))
                // 유튜브 구간이 하나라 맨 앞에 서고, 그 뒤는 공단 영상이 목록 차례로 잇는다
                .andExpect(jsonPath("$.clips[0].clipId").value("IdpXx2gm90o-518"))
                .andExpect(jsonPath("$.clips[0].title").value("버피"))
                .andExpect(jsonPath("$.clips[0].factor").value("순발력"))
                .andExpect(jsonPath("$.clips[1].clipId").value("0AUDLJ08S_00431-0"))
                .andExpect(jsonPath("$.clips[2].clipId", startsWith("0AUDLJ08S_")))
                .andExpect(jsonPath("$.clips[11].clipId", startsWith("0AUDLJ08S_")));
        list(parentUser, "profileId", childId.toString(), "q", " 스쿼트 ")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.clips[*].clipId", contains("IdpXx2gm90o-424", "IdpXx2gm90o-602")));
        // 성인 유튜브 「점프 스쿼트」 는 제목이 처방 어휘(앉았다 일어서면서 점프하기)라 영상에 뜬 이름으로는 찾히지 않는다.
        // 제목에 「스쿼트」 가 든 성인 공단 영상은 모두 근골격계운동(질환자용 표준운동)이라 V162 가 껐다 — 하나도 나오지 않는다
        list(parentUser, "q", "스쿼트")
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.clips[*].clipId", not(hasItem("In2fsTmsggw-114"))));
        // 같은 제목의 공단 영상(0AUDLJ08S_00311)이 영상 id 차례로 앞서 유튜브 구간 대신 대표로 남는다
        list(parentUser, "q", "점프하기")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.clips[*].clipId", contains("0AUDLJ08S_00311-0", "0AUDLJ08S_00325-0")))
                .andExpect(jsonPath("$.clips[0].title").value("앉았다 일어서면서 점프하기"));
        list(parentUser, "profileId", childId.toString(), "quiet", "true")
                .andExpect(jsonPath("$.total").value(129));
        list(parentUser, "profileId", childId.toString(), "phase", "WARMUP")
                .andExpect(jsonPath("$.total").value(28));
    }

    @Test
    @DisplayName("보호자가 아이 프로필의 구간 찜을 켜고 끈다 — 같은 값을 다시 보내도 한 행이고 처음 찜한 시각을 지킨다")
    void 보호자가_아이_프로필의_구간_찜을_켜고_끈다() throws Exception {
        favorite(parentUser, "IdpXx2gm90o-518", body(childId, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clipId").value("IdpXx2gm90o-518"))
                .andExpect(jsonPath("$.favorited").value(true));
        OffsetDateTime first = jdbc.queryForObject(
                "select created_at from exercise_favorites where profile_id = ?", OffsetDateTime.class, childId);
        favorite(parentUser, "IdpXx2gm90o-518", body(childId, true)).andExpect(status().isOk());

        assertThat(favoriteRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select created_at from exercise_favorites where profile_id = ?",
                        OffsetDateTime.class,
                        childId))
                .isEqualTo(first);
        list(parentUser, "list", "FAVORITES", "profileId", childId.toString())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.clips[0].clipId").value("IdpXx2gm90o-518"))
                .andExpect(jsonPath("$.clips[0].favorited").value(true));
        list(parentUser, "list", "FAVORITES", "profileId", parentId.toString())
                .andExpect(jsonPath("$.total").value(0));

        favorite(parentUser, "IdpXx2gm90o-518", body(childId, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));
        favorite(parentUser, "IdpXx2gm90o-518", body(childId, false)).andExpect(status().isOk());

        assertThat(favoriteRows()).isZero();
        list(parentUser, "list", "FAVORITES", "profileId", childId.toString())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("누구의 찜인지 없는 요청은 400 PROFILE_REQUIRED, 목록에 없는 구간은 404 CLIP_NOT_FOUND, 다른 가족은 403 이다")
    void 누구의_찜인지_없는_요청은_400_목록에_없는_구간은_404_다른_가족은_403_이다() throws Exception {
        list(parentUser, "list", "FAVORITES")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROFILE_REQUIRED"));
        favorite(parentUser, "IdpXx2gm90o-518", "{\"favorited\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROFILE_REQUIRED"));
        favorite(parentUser, "nope-0", body(childId, true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLIP_NOT_FOUND"));
        // 운동이 아닌 구간(휴식)은 목록에 나오지 않아 찜할 수 없다
        favorite(parentUser, "AW9qNySmp6I-192", body(childId, true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLIP_NOT_FOUND"));
        favorite(outsiderUser, "IdpXx2gm90o-518", body(childId, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        list(outsiderUser, "profileId", childId.toString()).andExpect(status().isForbidden());
        assertThat(favoriteRows()).isZero();
    }

    @Test
    @DisplayName("전환기 별칭 /clips(FE 가 부르는 이름)도 같은 핸들러다 — 목록 · 찜 · 오류 코드가 /exercises 와 같다")
    void 별칭_clips_도_같은_핸들러다() throws Exception {
        String viaAlias = mvc.perform(get("/api/v1/clips")
                        .param("profileId", childId.toString())
                        .param("factor", "유연성")
                        .param("quiet", "true")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String viaContract = list(parentUser, "profileId", childId.toString(), "factor", "유연성", "quiet", "true")
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(viaAlias).isEqualTo(viaContract).contains("\"clips\"").contains("\"total\"");

        mvc.perform(post("/api/v1/clips/IdpXx2gm90o-518/favorite")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(childId, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clipId").value("IdpXx2gm90o-518"))
                .andExpect(jsonPath("$.favorited").value(true));
        assertThat(favoriteRows()).isEqualTo(1);
        list(parentUser, "list", "FAVORITES", "profileId", childId.toString())
                .andExpect(jsonPath("$.clips[*].clipId", contains("IdpXx2gm90o-518")));

        mvc.perform(get("/api/v1/clips")
                        .param("list", "FAVORITES")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROFILE_REQUIRED"));
        mvc.perform(get("/api/v1/clips")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("모르는 단계 · 목록 종류 · 요인과 빠진 favorited 는 400 이다")
    void 모르는_단계_목록_종류_요인과_빠진_favorited_는_400_이다() throws Exception {
        list(parentUser, "phase", "STRETCH").andExpect(status().isBadRequest());
        list(parentUser, "list", "RECENT").andExpect(status().isBadRequest());
        list(parentUser, "factor", "협동심").andExpect(status().isBadRequest());
        favorite(parentUser, "IdpXx2gm90o-518", "{\"profileId\":\"" + childId + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
