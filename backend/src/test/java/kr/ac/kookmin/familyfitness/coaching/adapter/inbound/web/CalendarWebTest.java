package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway 위에서 GET /families/{familyId}/calendar 를 끝까지 돈다 — 응답 모양(FE types.ts CalendarView + ASKS 0-2 의
 * position · clip · verifiedBy), 쉬는 날 rest 는 true 일 때만 싣는지, 400 · 403 코드.
 * coaching · activity(활동 · 쉬는 날)는 실제 구현, identity 는 목이다(권한 · 받은 응원만 흉내 낸다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CalendarWebTest {
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

    @Autowired
    SessionCompletionRepository completions;

    @Autowired
    ActivityRecorder activity;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    CheerQuery cheerQuery;

    @MockitoBean
    AiGateway ai;

    private final UUID momUser = UUID.randomUUID();
    private final UUID kidUser = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(KST);
    private final LocalDate yesterday = today.minusDays(1);
    private UUID familyId;
    private UUID momId;
    private UUID kidId;
    private UUID sisterId;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        sisterId = rows.profile(familyId, LocalDate.of(2018, 5, 1), Sex.F, ProfileRole.CHILD, "서아");
        ProfileSummary mom = summary(momId, ProfileRole.PARENT);
        ProfileSummary kid = summary(kidId, ProfileRole.CHILD);
        ProfileSummary sister = summary(sisterId, ProfileRole.CHILD);
        when(familyAccess.requireMember(momUser, familyId)).thenReturn(mom);
        when(familyAccess.requireSameFamilyAsProfile(momUser, kidId)).thenReturn(kid);
        // 서준이는 제 계정이 있다
        when(familyAccess.requireMember(kidUser, familyId)).thenReturn(kid);
        when(familyAccess.requireSameFamilyAsProfile(kidUser, kidId)).thenReturn(kid);
        when(familyAccess.requireSameFamilyAsProfile(kidUser, sisterId)).thenReturn(sister);
        when(profileQuery.summariesOfFamily(familyId)).thenReturn(List.of(mom, kid, sister));
    }

    private ProfileSummary summary(UUID profileId, ProfileRole role) {
        boolean child = role == ProfileRole.CHILD;
        return new ProfileSummary(
                profileId,
                familyId,
                child ? "서준" : "엄마",
                role,
                child ? AgeGroup.YOUTH : AgeGroup.ADULT,
                child ? Sex.M : Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                child,
                true);
    }

    private ResultActions calendar(UUID user, UUID profileId, LocalDate from, LocalDate to) throws Exception {
        return mvc.perform(get("/api/v1/families/" + familyId + "/calendar")
                .param("profileId", profileId.toString())
                .param("from", from.toString())
                .param("to", to.toString())
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(user)));
    }

    /** 어제 하루짜리: 준비 1분 · 본 2분(영상 구간) · 정리 1분. 서준이가 앞의 두 칸을 끝냈다(150초). */
    private Mission yesterdayMission() {
        Mission mission = missions.save(Mission.manual(
                UUID.randomUUID(),
                familyId,
                "스쿼트 중심 4분",
                TargetMetric.TIMER_MINUTES,
                4,
                null,
                yesterday,
                yesterday,
                List.of(kidId, momId),
                List.of(
                        new MissionSession(1, SessionPhase.WARMUP, "제자리 걷기", null, 1, null),
                        new MissionSession(
                                2, SessionPhase.MAIN, "스쿼트", null, 2, new SessionClip("vid-squat", 30, 150, "스쿼트")),
                        new MissionSession(3, SessionPhase.COOLDOWN, "숨 고르기", null, 1, null)),
                momId,
                Instant.now()));
        em.flush();
        Instant at = yesterday.atTime(19, 0).atZone(KST).toInstant();
        completions.insert(
                new SessionCompletion(mission.getId(), 1, kidId, at, yesterday, 60, VerifiedBy.VIDEO_PROGRESS));
        completions.insert(
                new SessionCompletion(mission.getId(), 2, kidId, at, yesterday, 90, VerifiedBy.VIDEO_PROGRESS));
        activity.addActiveSeconds(kidId, yesterday, ActivitySource.VIDEO, 150);
        return mission;
    }

    private void restCard(LocalDate day) {
        jdbc.update("""
                insert into rest_cards (id, family_id, rest_date, rest_month, card_no, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?)\
                """, UUID.randomUUID(), familyId, day, day.withDayOfMonth(1), 1, momId, Instant.now());
    }

    @Test
    @DisplayName("응답 모양 — 칸마다 position · clip · verifiedBy · done, 분은 서버가 잰 초 ÷ 60, 쉬는 날만 rest 를 싣는다")
    void 응답_모양() throws Exception {
        Mission mission = yesterdayMission();
        restCard(today.plusDays(1));
        Instant stuckAt = yesterday.atTime(20, 0).atZone(KST).toInstant();
        when(cheerQuery.received(eq(kidId), any(), any()))
                .thenReturn(List.of(new CheerView(
                        UUID.randomUUID(),
                        momId,
                        "엄마",
                        kidId,
                        CheerKind.PRAISE,
                        "멋져",
                        "star",
                        mission.getId(),
                        null,
                        stuckAt)));

        String body = calendar(momUser, kidId, yesterday.minusDays(2), today.plusDays(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(kidId.toString()))
                .andExpect(jsonPath("$.from").value(yesterday.minusDays(2).toString()))
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[0].date").value(yesterday.toString()))
                .andExpect(jsonPath("$.days[0].minutes").value(2))
                .andExpect(jsonPath("$.days[0].plannedMinutes").value(4))
                .andExpect(jsonPath("$.days[0].entries[0].missionId")
                        .value(mission.getId().toString()))
                .andExpect(jsonPath("$.days[0].entries[0].title").value("스쿼트 중심 4분"))
                .andExpect(jsonPath("$.days[0].entries[0].minutes").value(3))
                .andExpect(jsonPath("$.days[0].entries[0].completed").value(false))
                .andExpect(jsonPath("$.days[0].entries[0].sessions", hasSize(3)))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[1].position").value(2))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[1].phase").value("MAIN"))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[1].title").value("스쿼트"))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[1].minutes").value(2))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[1].clip.videoId")
                        .value("vid-squat"))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[1].clip.startSec")
                        .value(30))
                .andExpect(
                        jsonPath("$.days[0].entries[0].sessions[1].clip.endSec").value(150))
                .andExpect(
                        jsonPath("$.days[0].entries[0].sessions[1].verifiedBy").value("VIDEO_PROGRESS"))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[1].done").value(true))
                .andExpect(
                        jsonPath("$.days[0].entries[0].sessions[2].verifiedBy").value(nullValue()))
                .andExpect(jsonPath("$.days[0].entries[0].sessions[2].done").value(false))
                .andExpect(jsonPath("$.days[0].stickers[0].stickerId").value("star"))
                .andExpect(jsonPath("$.days[0].stickers[0].fromName").value("엄마"))
                .andExpect(jsonPath("$.days[0].stickers[0].missionId")
                        .value(mission.getId().toString()))
                .andExpect(jsonPath("$.days[1].date").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.days[1].rest").value(true))
                .andExpect(jsonPath("$.days[1].plannedMinutes").value(nullValue()))
                .andExpect(jsonPath("$.days[1].entries", hasSize(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<String, Object> first = JsonPath.read(body, "$.days[0]");
        assertThat(first).containsKey("plannedMinutes").doesNotContainKey("rest");
    }

    @Test
    @DisplayName("기간 — 43일 · 거꾸로 된 기간 · 빠진 값은 400 BAD_REQUEST")
    void 기간이_틀리면_400() throws Exception {
        calendar(momUser, kidId, today.minusDays(42), today)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        calendar(momUser, kidId, today, today.minusDays(1)).andExpect(status().isBadRequest());
        calendar(momUser, kidId, today.minusDays(41), today).andExpect(status().isOk());
        mvc.perform(get("/api/v1/families/" + familyId + "/calendar")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(momUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("자녀 계정은 자기 것만 — 형제 것은 403 NOT_A_PARENT")
    void 자녀_계정은_자기_것만() throws Exception {
        calendar(kidUser, kidId, today.minusDays(6), today).andExpect(status().isOk());
        calendar(kidUser, sisterId, today.minusDays(6), today)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
    }
}
