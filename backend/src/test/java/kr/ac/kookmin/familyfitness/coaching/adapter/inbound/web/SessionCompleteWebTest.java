package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway(V144) 위에서 POST /missions/{missionId}/sessions/{seq}/complete 를 끝까지 돈다.
 * activity · progress · coaching 은 실제 구현이다 — xpGained 가 /progress 가 실제로 는 만큼인지 본다.
 * identity 는 목: 이 프로필 이름으로 할 수 있는지 · 같은 가족인지 · 가족 구성원 목록만 흉내 낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SessionCompleteWebTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    MissionRepository missions;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    CheerQuery cheerQuery;

    @MockitoBean
    AiGateway ai;

    private final UUID momUser = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(KST);
    private UUID familyId;
    private UUID momId;
    private UUID kidId;
    private ProfileSummary kid;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        ProfileSummary mom = summary(momId, ProfileRole.PARENT, true);
        kid = summary(kidId, ProfileRole.CHILD, true);
        // 부모 폰을 아이가 빌려 쓴다 — 엄마 계정이 계정 없는 아이 이름으로 보낸다
        when(familyAccess.requireActingAs(momUser, kidId)).thenReturn(kid);
        when(familyAccess.requireActingAs(momUser, momId)).thenReturn(mom);
        when(familyAccess.requireMember(momUser, familyId)).thenReturn(mom);
        when(familyAccess.requireSameFamilyAsProfile(momUser, kidId)).thenReturn(kid);
        when(familyAccess.requireSameFamilyAsProfile(momUser, momId)).thenReturn(mom);
        when(profileQuery.summariesOfFamily(familyId)).thenReturn(List.of(mom, kid));
    }

    private ProfileSummary summary(UUID profileId, ProfileRole role, boolean consentGiven) {
        boolean child = role == ProfileRole.CHILD;
        return new ProfileSummary(
                profileId,
                familyId,
                child ? "서준" : "엄마",
                role,
                child ? AgeGroup.YOUTH : AgeGroup.ADULT,
                child ? Sex.M : Sex.F,
                !child,
                child ? InviteStatus.NONE : InviteStatus.CLAIMED,
                null,
                consentGiven,
                child,
                consentGiven,
                false);
    }

    /** 오늘(또는 그 날) 하루짜리 직접 짜기: 준비 1분 · 본 2분. 엄마 · 아이가 같이 한다. */
    private Mission mission(LocalDate day) {
        Mission saved = missions.save(Mission.manual(
                UUID.randomUUID(),
                familyId,
                "거북이 스트레칭",
                TargetMetric.TIMER_MINUTES,
                3,
                null,
                day,
                day,
                List.of(kidId, momId),
                List.of(
                        new MissionSession(1, SessionPhase.WARMUP, "제자리 걷기", null, 1, null),
                        new MissionSession(2, SessionPhase.MAIN, "거북이 스트레칭", FitnessFactor.FLEXIBILITY, 2, null)),
                momId,
                Instant.now()));
        em.flush();
        return saved;
    }

    private ResultActions complete(Object missionId, int seq, UUID profileId, int activeSeconds) throws Exception {
        Instant endedAt = Instant.now();
        return complete(missionId, seq, profileId, activeSeconds, endedAt.minusSeconds(600), endedAt);
    }

    private ResultActions complete(
            Object missionId, int seq, UUID profileId, int activeSeconds, Instant startedAt, Instant endedAt)
            throws Exception {
        return mvc.perform(post("/api/v1/missions/" + missionId + "/sessions/" + seq + "/complete")
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + profileId + "\",\"activeSeconds\":" + activeSeconds + ",\"startedAt\":\""
                        + startedAt + "\",\"endedAt\":\"" + endedAt + "\"}"));
    }

    /** 전환기 별칭 {@code /done}(FE 가 부르는 이름)으로 보낸다. 몸통은 {@link #complete} 와 같다. */
    private ResultActions done(Object missionId, int seq, String body) throws Exception {
        return mvc.perform(post("/api/v1/missions/" + missionId + "/sessions/" + seq + "/done")
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int xpOf(UUID profileId) throws Exception {
        String body = mvc.perform(get("/api/v1/profiles/" + profileId + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.xp");
    }

    @Test
    @DisplayName("칸 끝 — 응답 xpGained 가 /progress 가 는 만큼이고, 같은 칸을 다시 보내면 200 · 0. 끝낸 칸은 사람마다 doneSessions 로 실린다")
    void 칸_끝의_경험치는_progress_가_는_만큼이다() throws Exception {
        Mission mission = mission(today);

        complete(mission.getId(), 1, kidId, 45)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.verifiedBy").value("VIDEO_PROGRESS"))
                .andExpect(jsonPath("$.missionProgress").value(0.333))
                .andExpect(jsonPath("$.missionCompleted").value(false))
                .andExpect(jsonPath("$.xpGained").value(5));
        assertThat(xpOf(kidId)).isEqualTo(5);
        // 아이가 끝낸 칸은 같이 한 엄마에게도 — 엄마도 칸 +5
        assertThat(xpOf(momId)).isEqualTo(5);

        complete(mission.getId(), 1, kidId, 45)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xpGained").value(0))
                .andExpect(jsonPath("$.missionProgress").value(0.333));
        assertThat(xpOf(kidId)).isEqualTo(5);

        complete(mission.getId(), 2, kidId, 120)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionProgress").value(1.0))
                .andExpect(jsonPath("$.missionCompleted").value(true))
                .andExpect(jsonPath("$.xpGained").value(25));
        assertThat(xpOf(kidId)).isEqualTo(30);

        String kidPath = "$.participants[?(@.profileId=='" + kidId + "')]";
        String momPath = "$.participants[?(@.profileId=='" + momId + "')]";
        mvc.perform(get("/api/v1/missions/" + mission.getId()).header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath(kidPath + ".doneSessions[*]", contains(1, 2)))
                .andExpect(jsonPath(kidPath + ".completed").value(true))
                .andExpect(jsonPath(kidPath + ".verifiedBy").value("VIDEO_PROGRESS"))
                .andExpect(jsonPath(momPath + ".doneSessions[*]", contains(1, 2)));
        em.flush();
        assertThat(jdbc.queryForList(
                        "select completed_on from mission_session_completions where mission_id = ? and profile_id = ?",
                        LocalDate.class,
                        mission.getId(),
                        kidId))
                .containsExactly(today, today);
        assertThat(jdbc.queryForObject(
                        "select active_seconds from activity_daily where profile_id = ? and source = 'VIDEO'",
                        Integer.class,
                        kidId))
                .isEqualTo(165);
        // 45 + 120 = 165초 → 2분(내림), 그래도 움직인 날 하루
        mvc.perform(get("/api/v1/profiles/" + kidId + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser)))
                .andExpect(jsonPath("$.activeDays").value(1))
                .andExpect(jsonPath("$.streakDays").value(1));
    }

    @Test
    @DisplayName("사용자 입력 오류(칸 끝 endedAt ≤ startedAt · 날짜 칸 둘 다 보내기)는 400 BAD_REQUEST 이고, 서버 버그처럼 스택을 WARN 으로 남기지 않는다")
    void 입력_오류는_400_이고_스택을_남기지_않는다(CapturedOutput output) throws Exception {
        Mission mission = mission(today);
        Instant at = Instant.now();

        complete(mission.getId(), 1, kidId, 60, at, at)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("endedAt 은 startedAt 이후여야 합니다"));
        mvc.perform(post("/api/v1/families/" + familyId + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"걷기\",\"startDate\":\"" + today + "\",\"endDate\":\"" + today
                                + "\",\"dates\":[\"" + today
                                + "\"],\"targetMetric\":\"TIMER_MINUTES\",\"targetValue\":10,"
                                + "\"participantProfileIds\":[\"" + kidId + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        assertThat(output).doesNotContain("IllegalArgumentException");
    }

    @Test
    @DisplayName(
            "판정 차례와 상태 코드 — 404 MISSION_NOT_FOUND · 404 SESSION_NOT_FOUND · 403 · 403 NOT_A_PARTICIPANT · 422 CONSENT_REQUIRED · 422 MISSION_NOT_ACTIVE · 400 · 422 TOO_SHORT")
    void 판정_차례와_상태_코드() throws Exception {
        Mission mission = mission(today);
        UUID strangerKid = UUID.randomUUID();
        when(familyAccess.requireActingAs(momUser, strangerKid)).thenThrow(new CannotActAsProfileException());

        complete(UUID.randomUUID(), 1, kidId, 60)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MISSION_NOT_FOUND"));
        complete(mission.getId(), 3, kidId, 60)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        complete(mission.getId(), 1, strangerKid, 60).andExpect(status().isForbidden());
        Mission kidOnly = missions.save(Mission.manual(
                UUID.randomUUID(),
                familyId,
                "혼자",
                TargetMetric.TIMER_MINUTES,
                1,
                null,
                today,
                today,
                List.of(kidId),
                List.of(new MissionSession(1, SessionPhase.MAIN, "동작", null, 1, null)),
                momId,
                Instant.now()));
        complete(kidOnly.getId(), 1, momId, 60)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARTICIPANT"));
        complete(mission(today.plusDays(1)).getId(), 1, kidId, 60)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("MISSION_NOT_ACTIVE"));
        Instant at = Instant.now();
        complete(mission.getId(), 1, kidId, 60, at, at).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/missions/" + mission.getId() + "/sessions/1/complete")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + kidId + "\",\"activeSeconds\":60}"))
                .andExpect(status().isBadRequest());
        complete(mission.getId(), 1, kidId, 29)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("TOO_SHORT"));

        when(familyAccess.requireActingAs(momUser, kidId)).thenReturn(summary(kidId, ProfileRole.CHILD, false));
        complete(mission.getId(), 1, kidId, 60)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        em.flush();
        assertThat(Objects.requireNonNull(jdbc.queryForObject(
                        "select count(*) from mission_session_completions where profile_id = ?", Integer.class, kidId)))
                .isZero();
    }

    @Test
    @DisplayName("전환기 별칭 /done(FE 가 부르는 이름)도 같은 핸들러다 — 같은 답을 주고, 그 칸을 /complete 로 다시 보내면 이미 끝낸 칸(200 · 0)")
    void 별칭_done_도_같은_핸들러다() throws Exception {
        Mission mission = mission(today);
        Instant endedAt = Instant.now();
        String body = "{\"profileId\":\"" + kidId + "\",\"activeSeconds\":45,\"startedAt\":\""
                + endedAt.minusSeconds(600) + "\",\"endedAt\":\"" + endedAt + "\"}";

        done(mission.getId(), 1, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.verifiedBy").value("VIDEO_PROGRESS"))
                .andExpect(jsonPath("$.missionProgress").value(0.333))
                .andExpect(jsonPath("$.missionCompleted").value(false))
                .andExpect(jsonPath("$.xpGained").value(5));
        complete(mission.getId(), 1, kidId, 45)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xpGained").value(0));
        assertThat(xpOf(kidId)).isEqualTo(5);

        // 몸통 검사 · 판정도 같다
        done(mission.getId(), 1, "{\"profileId\":\"" + kidId + "\",\"activeSeconds\":60}")
                .andExpect(status().isBadRequest());
        done(mission.getId(), 3, body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        mvc.perform(post("/api/v1/missions/" + mission.getId() + "/sessions/1/done")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
