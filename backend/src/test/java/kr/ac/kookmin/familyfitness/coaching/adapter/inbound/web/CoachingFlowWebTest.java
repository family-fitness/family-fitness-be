package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity;
import kr.ac.kookmin.familyfitness.coaching.application.AppTime;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Summaries;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * H2 + Flyway(시드) 위에서 코치 실행 → 승인 → 미션 → 활동 → 영상 → 대화 → 주간 요약의 전체 흐름.
 * identity·fitness·activity 는 이 워크트리에 구현이 없으므로 공개 포트를 {@link MockitoBean} 으로 대신하고,
 * 비동기 실행기는 동기 {@link SyncTaskExecutor} 로 바꿔 커밋 직후(AFTER_COMMIT) 결정적으로 끝나게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CoachingFlowWebTest.SyncAsync.class)
@TestPropertySource(properties = {"app.coach.poll-interval-ms=0", "app.coach.max-polls=3"})
class CoachingFlowWebTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class SyncAsync implements AsyncConfigurer {
        @Override
        public Executor getAsyncExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestAuth auth;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AppTime time;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    CheerQuery cheerQuery;

    @MockitoBean
    FitnessQuery fitnessQuery;

    @MockitoBean
    ActivityRecorder activityRecorder;

    @MockitoBean
    ActivityQuery activityQuery;

    private final Family family = new Family();
    private ActivityTotals childTotals = new ActivityTotals(0, 0, 0);

    private UUID familyId() {
        return family.familyId;
    }

    private UUID childId() {
        return family.child.profileId();
    }

    private UUID parentId() {
        return family.parent.profileId();
    }

    @BeforeEach
    void setUp() {
        reset(profileQuery, familyAccess, cheerQuery, fitnessQuery, activityRecorder, activityQuery);
        insertFamilyRows();
        LocalDate today = time.today();
        ProfileSummary parentSummary = Summaries.summary(family.parent, today);
        ProfileSummary childSummary = Summaries.summary(family.child, today);
        ProfileSummary cheerSummary = Summaries.summary(family.cheerParent, today);

        given(profileQuery.summariesOfFamily(familyId()))
                .willReturn(List.of(parentSummary, childSummary, cheerSummary));
        given(profileQuery.detailsOfFamily(familyId())).willReturn(family.members());
        given(familyAccess.requireMember(family.parentUser, familyId())).willReturn(parentSummary);
        given(familyAccess.requireMember(family.childUser, familyId())).willReturn(childSummary);
        given(familyAccess.requireMember(eq(family.outsiderUser), any())).willThrow(new NotSameFamilyException());
        given(familyAccess.requireParent(family.parentUser, familyId())).willReturn(parentSummary);
        given(familyAccess.requireParent(family.childUser, familyId())).willThrow(new NotAParentException());
        given(familyAccess.requireSameFamilyAsProfile(any(), eq(childId()))).willReturn(childSummary);
        given(familyAccess.requireSameFamilyAsProfile(any(), eq(parentId()))).willReturn(parentSummary);
        given(fitnessQuery.hasAnyTest(any())).willReturn(true);
        given(fitnessQuery.latestOf(childId()))
                .willReturn(new LatestFitness(
                        childId(),
                        UUID.randomUUID(),
                        today.minusDays(2),
                        new BigDecimal("140.5"),
                        new BigDecimal("35.0"),
                        Map.of("012", new BigDecimal("8.0")),
                        null,
                        null));
        given(activityRecorder.addActiveMinutes(any(), any(), any(), anyInt()))
                .willAnswer(inv -> new DailyActivity(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), 0, inv.getArgument(3)));
        given(activityRecorder.overwriteSteps(any(), any(), anyInt()))
                .willAnswer(inv -> new DailyActivity(
                        inv.getArgument(0), inv.getArgument(1), ActivitySource.MANUAL, inv.getArgument(2), 0));
        given(activityQuery.totals(eq(childId()), any(), any())).willAnswer(inv -> childTotals);
        given(activityQuery.totals(eq(parentId()), any(), any())).willReturn(new ActivityTotals(0, 0, 0));
        given(activityQuery.totals(eq(family.cheerParent.profileId()), any(), any()))
                .willReturn(new ActivityTotals(0, 0, 0));
        given(activityQuery.activeMinutesOn(eq(childId()), any())).willReturn(20);
        given(cheerQuery.countCheers(eq(familyId()), any(), any())).willReturn(2);
    }

    @Test
    @DisplayName("코치 실행부터 주간 요약까지 한 흐름")
    void 코치_실행부터_주간_요약까지_한_흐름() throws Exception {
        String parent = auth.bearer(family.parentUser);
        String child = auth.bearer(family.childUser);

        // 시작(202) — 동기 실행기라 응답 시점에 이미 끝나 있다.
        MvcResult startResult = mockMvc.perform(post("/api/v1/families/" + familyId() + "/coach/runs")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daysPerWeek\":3,\"minutesPerSession\":15}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.pollAfterMs").value(1500))
                .andReturn();
        String runId = extract("\"coachRunId\":\"([^\"]+)\"", startResult);

        // 같은 주 재시작 → 409
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/coach/runs")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_RUN_THIS_WEEK"));

        // 조회: 부모는 승인 가능, 제안에 시드 영상 제목·배지
        mockMvc.perform(get("/api/v1/coach/runs/" + runId).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.canApprove").value(true))
                .andExpect(jsonPath("$.missionCount").value(0))
                .andExpect(jsonPath("$.weekStart").value(time.thisWeekStart().toString()))
                .andExpect(jsonPath("$.steps", hasSize(4)))
                .andExpect(jsonPath("$.steps[0].name").value("assess"))
                .andExpect(jsonPath("$.proposals", hasSize(1)))
                .andExpect(jsonPath("$.proposals[0].targetMetric").value("TIMER_MINUTES"))
                .andExpect(jsonPath("$.proposals[0].targetValue").value(45))
                .andExpect(jsonPath("$.proposals[0].video.videoId").value("IdpXx2gm90o"))
                .andExpect(jsonPath("$.proposals[0].video.title").value("초등학생의 기초체력향상과 운동능력발달을 위한 운동"))
                .andExpect(jsonPath("$.proposals[0].video.startSec").value(96))
                .andExpect(jsonPath("$.proposals[0].video.badges[0]").value("조용함"))
                .andExpect(jsonPath("$.proposals[0].participants", hasSize(2)))
                .andExpect(jsonPath("$.proposals[0].citations", hasSize(2)))
                .andExpect(jsonPath("$.proposals[0].citations[1].chunkId").value("video:IdpXx2gm90o"));
        mockMvc.perform(get("/api/v1/coach/runs/" + runId).header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canApprove").value(false));

        // 자녀 승인 → 403, 부모 승인 → 미션 생성, 재승인 → 409
        mockMvc.perform(post("/api/v1/coach/runs/" + runId + "/approve").header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        MvcResult approveResult = mockMvc.perform(
                        post("/api/v1/coach/runs/" + runId + "/approve").header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value(parentId().toString()))
                .andExpect(jsonPath("$.createdMissions", hasSize(1)))
                .andExpect(jsonPath("$.createdMissions[0].origin").value("COACH"))
                .andReturn();
        String missionId = extract("\"missionId\":\"([^\"]+)\"", approveResult);
        mockMvc.perform(post("/api/v1/coach/runs/" + runId + "/approve").header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_APPROVED"));

        // 미션 목록
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/missions").header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions", hasSize(1)))
                .andExpect(jsonPath("$.missions[0].missionId").value(missionId))
                .andExpect(jsonPath("$.missions[0].origin").value("COACH"))
                .andExpect(jsonPath("$.missions[0].coachRunId").value(runId))
                .andExpect(jsonPath("$.missions[0].serverVerifiable").value(true))
                .andExpect(jsonPath("$.missions[0].video.durationSec").value(600))
                .andExpect(jsonPath("$.missions[0].sessions", hasSize(0)))
                .andExpect(jsonPath("$.missions[0].participants", hasSize(2)))
                .andExpect(jsonPath("$.missions[0].participants[?(@.profileId=='" + childId() + "')].name")
                        .value("민준"))
                .andExpect(jsonPath("$.missions[0].participants[?(@.profileId=='" + childId() + "')].progress")
                        .value(0.0));

        // 타이머: 경과 20분으로 자르고, 활동 합계(모의) 45분 → 완료
        childTotals = new ActivityTotals(0, 45, 45);
        Instant startedAt = time.today().atTime(8, 30).atZone(time.getZone()).toInstant();
        mockMvc.perform(post("/api/v1/missions/" + missionId + "/activity/timer")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"startedAt\":\"" + startedAt
                                + "\",\"endedAt\":\"" + startedAt.plusSeconds(20 * 60)
                                + "\",\"activeMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityDate").value(time.today().toString()))
                .andExpect(jsonPath("$.source").value("TIMER"))
                .andExpect(jsonPath("$.serverVerified").value(true))
                .andExpect(jsonPath("$.totalActiveMinutes").value(20))
                .andExpect(jsonPath("$.missionProgress").value(1.0))
                .andExpect(jsonPath("$.missionCompleted").value(true));
        verify(activityRecorder).addActiveMinutes(childId(), time.today(), ActivitySource.TIMER, 20);

        // 영상 진행률: 최초 완주에만 10분 적립
        mockMvc.perform(post("/api/v1/videos/IdpXx2gm90o/progress")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"progress\":0.95,\"watchedSec\":570}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxProgress").value(0.95))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.creditedMinutes").value(10))
                .andExpect(jsonPath("$.verifiedBy").value("VIDEO_PROGRESS"));
        mockMvc.perform(post("/api/v1/videos/IdpXx2gm90o/progress")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId()
                                + "\",\"progress\":1.0,\"watchedSec\":600,\"missionId\":\"" + missionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditedMinutes").value(0))
                .andExpect(jsonPath("$.missionProgress").value(1.0));
        mockMvc.perform(post("/api/v1/videos/nope/progress")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"progress\":0.5,\"watchedSec\":10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VIDEO_NOT_FOUND"));

        // 대화(스텁): 인용 있는 답, 의료 질문은 거부(200)
        MvcResult chatResult = mockMvc.perform(post("/api/v1/coach/chat")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"question\":\"유연성에 좋은 준비운동이 뭐예요?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].sourceLabel").value("국민체력100 운동처방 · 유소년 11세"))
                .andExpect(jsonPath("$.citations[0].excerpt").value("국민체력100 운동처방 · 유소년 11세"))
                .andReturn();
        String conversationId = extract("\"conversationId\":\"([^\"]+)\"", chatResult);
        mockMvc.perform(post("/api/v1/coach/chat")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"conversationId\":\"" + conversationId
                                + "\",\"question\":\"무릎 통증이 있어요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.refusalReason").value("medical_query"));
        mockMvc.perform(post("/api/v1/coach/chat")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + parentId() + "\",\"conversationId\":\"" + conversationId
                                + "\",\"question\":\"내 대화가 아닌데요\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // 주간 요약
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/report/weekly")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value(time.thisWeekStart().toString()))
                .andExpect(jsonPath("$.weekEnd")
                        .value(time.thisWeekStart().plusDays(6).toString()))
                .andExpect(jsonPath("$.summary").value("유연성은 매일 조금씩 늘려 가는 영역입니다. 한 주 3회, 회당 15분이면 충분합니다."))
                .andExpect(jsonPath("$.missionStats.total").value(1))
                .andExpect(jsonPath("$.missionStats.completed").value(0))
                .andExpect(jsonPath("$.members", hasSize(3)))
                .andExpect(jsonPath("$.members[?(@.profileId=='" + childId() + "')].verifiedMinutes")
                        .value(45))
                .andExpect(jsonPath("$.members[?(@.profileId=='" + childId() + "')].completedMissions")
                        .value(1))
                .andExpect(jsonPath("$.cheerCount").value(2));

        // 인증 없음 → 401, 다른 가족 → 403
        mockMvc.perform(get("/api/v1/coach/runs/" + runId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(family.outsiderUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
    }

    @Test
    @DisplayName("직접 만든 걸음수 미션은 보호자 확인으로 끝나고 영상 목록은 필터·즐겨찾기를 지원한다")
    void 직접_만든_걸음수_미션은_보호자_확인으로_끝나고_영상_목록은_필터_즐겨찾기를_지원한다() throws Exception {
        String parent = auth.bearer(family.parentUser);
        String child = auth.bearer(family.childUser);
        LocalDate today = time.today();

        mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"걷기\",\"startDate\":\"" + today + "\",\"endDate\":\"" + today
                                + "\",\"targetMetric\":\"STEPS\",\"targetValue\":3000,\"participantProfileIds\":[\""
                                + childId() + "\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"startDate\":\"" + today + "\",\"endDate\":\"" + today
                                + "\",\"targetMetric\":\"STEPS\",\"targetValue\":3000,\"participantProfileIds\":[\""
                                + childId() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"걷기\",\"startDate\":\"" + today + "\",\"endDate\":\"" + today
                                + "\",\"targetMetric\":\"STEPS\",\"targetValue\":3000,\"participantProfileIds\":[\""
                                + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("NOT_FAMILY_MEMBER"));
        MvcResult created = mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"걷기\",\"startDate\":\"" + today + "\",\"endDate\":\""
                                + today.plusDays(2)
                                + "\",\"targetMetric\":\"STEPS\",\"targetValue\":3000,\"participantProfileIds\":[\""
                                + childId() + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.coachRunId", nullValue()))
                .andExpect(jsonPath("$.serverVerifiable").value(false))
                .andReturn();
        String missionId = extract("\"missionId\":\"([^\"]+)\"", created);

        mockMvc.perform(post("/api/v1/missions/" + missionId + "/participants/" + childId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("TARGET_NOT_REACHED"));

        childTotals = new ActivityTotals(3500, 0, 0);
        mockMvc.perform(post("/api/v1/missions/" + missionId + "/activity/steps")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"activityDate\":\"" + today
                                + "\",\"steps\":3500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.serverVerified").value(false))
                .andExpect(jsonPath("$.verifiedBy").value("SELF_REPORT"))
                .andExpect(jsonPath("$.missionProgress").value(1.0))
                .andExpect(jsonPath("$.missionCompleted").value(false))
                .andExpect(jsonPath("$.needsGuardianCheck").value(true));
        mockMvc.perform(post("/api/v1/missions/" + missionId + "/activity/timer")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"startedAt\":\""
                                + Instant.now().minusSeconds(600) + "\",\"endedAt\":\"" + Instant.now()
                                + "\",\"activeMinutes\":5}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_METRIC"));
        mockMvc.perform(post("/api/v1/missions/" + missionId + "/participants/" + childId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.verifiedBy").value("SELF_REPORT"))
                .andExpect(jsonPath("$.confirmedBy").value(parentId().toString()));
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/missions?scope=MINE&status=DONE")
                        .header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions", hasSize(1)))
                .andExpect(jsonPath("$.missions[0].participants[0].completed").value(true))
                .andExpect(jsonPath("$.missions[0].participants[0].needsGuardianCheck")
                        .value(false));

        // 영상 목록: 유소년 안전 필터 + 요인, 커서, 즐겨찾기
        mockMvc.perform(get("/api/v1/videos?ageGroup=유소년&factor=유연성&size=1").header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos", hasSize(1)))
                .andExpect(jsonPath("$.videos[0].videoId").value("IdpXx2gm90o"))
                .andExpect(
                        jsonPath("$.videos[0].thumbnailUrl").value("https://i.ytimg.com/vi/IdpXx2gm90o/hqdefault.jpg"))
                .andExpect(jsonPath("$.videos[0].label.factors[0]").value("유연성"))
                .andExpect(jsonPath("$.videos[0].favorited").value(false))
                .andExpect(jsonPath("$.nextCursor").value("IdpXx2gm90o"));
        mockMvc.perform(get("/api/v1/videos?ageGroup=유소년&factor=유연성&size=1&cursor=IdpXx2gm90o")
                        .header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos[0].videoId").value("sample00002"))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
        mockMvc.perform(get("/api/v1/videos?list=FAVORITES").header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/videos/sample00003/favorite")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
        mockMvc.perform(get("/api/v1/videos?list=FAVORITES&profileId=" + childId())
                        .header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos", hasSize(1)))
                .andExpect(jsonPath("$.videos[0].videoId").value("sample00003"))
                .andExpect(jsonPath("$.videos[0].favorited").value(true))
                .andExpect(jsonPath("$.videos[0].maxProgress", closeTo(0.0, 0.0001)));
    }

    @Test
    @DisplayName("직접 만들기의 칸은 보낸 차례대로 저장되고 목록 · 단건에 같은 모양으로 실린다")
    void 직접_만들기의_칸은_보낸_차례대로_저장되고_목록_단건에_같은_모양으로_실린다() throws Exception {
        String parent = auth.bearer(family.parentUser);
        String child = auth.bearer(family.childUser);
        LocalDate today = time.today();
        // 화면(routine.ts toSessions)이 보내는 모양 그대로 — completed · verifiedBy 는 버린다.
        // 정리운동이 1번이어도 단계로 다시 세우지 않는다. -EATykJOvBQ 는 카탈로그에 없어도 받는다(사본).
        String sessions = """
                [{"position":1,"phase":"COOLDOWN","title":"거북이 스트레칭","factor":"유연성","minutes":2,
                  "clip":{"videoId":"-EATykJOvBQ","startSec":6,"endSec":78,"title":"거북이 스트레칭"},
                  "completed":false,"verifiedBy":null},
                 {"position":2,"phase":"WARMUP","title":"제자리 걷기","factor":null,"minutes":3,
                  "clip":{"videoId":"IdpXx2gm90o","startSec":96,"endSec":150},
                  "completed":false,"verifiedBy":null}]""";

        MvcResult created = mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missionBody(today, "TIMER_MINUTES", 5, sessions)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andReturn();
        String missionId = extract("\"missionId\":\"([^\"]+)\"", created);

        mockMvc.perform(get("/api/v1/missions/" + missionId).header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value(missionId))
                .andExpect(jsonPath("$.targetMetric").value("TIMER_MINUTES"))
                .andExpect(jsonPath("$.targetValue").value(5))
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.sessions", hasSize(2)))
                .andExpect(jsonPath("$.sessions[0].position").value(1))
                .andExpect(jsonPath("$.sessions[0].phase").value("COOLDOWN"))
                .andExpect(jsonPath("$.sessions[0].title").value("거북이 스트레칭"))
                .andExpect(jsonPath("$.sessions[0].factor").value("유연성"))
                .andExpect(jsonPath("$.sessions[0].minutes").value(2))
                .andExpect(jsonPath("$.sessions[0].clip.videoId").value("-EATykJOvBQ"))
                .andExpect(jsonPath("$.sessions[0].clip.startSec").value(6))
                .andExpect(jsonPath("$.sessions[0].clip.endSec").value(78))
                .andExpect(jsonPath("$.sessions[0].clip.title").value("거북이 스트레칭"))
                .andExpect(jsonPath("$.sessions[0].completed").doesNotExist())
                .andExpect(jsonPath("$.sessions[1].position").value(2))
                .andExpect(jsonPath("$.sessions[1].phase").value("WARMUP"))
                .andExpect(jsonPath("$.sessions[1].factor", nullValue()))
                .andExpect(jsonPath("$.sessions[1].clip.title", nullValue()));
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/missions").header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions", hasSize(1)))
                .andExpect(jsonPath("$.missions[0].sessions", hasSize(2)))
                .andExpect(jsonPath("$.missions[0].sessions[0].phase").value("COOLDOWN"))
                .andExpect(jsonPath("$.missions[0].sessions[1].clip.videoId").value("IdpXx2gm90o"));
        assertThat(jdbc.sql("select phase from mission_sessions where mission_id = ? order by position")
                        .param(UUID.fromString(missionId))
                        .query(String.class)
                        .list())
                .containsExactly("COOLDOWN", "WARMUP");

        // 칸 규칙 위반은 400 이고 아무것도 저장하지 않는다. targetValue 는 칸 합에 맞춰 각 규칙만 어기게 한다
        List<String> invalid = List.of(
                missionBody(today, "TIMER_MINUTES", 1, "[" + sessionJson(1, 1, "IdpXx2gm90o", 96, 96) + "]"),
                missionBody(
                        today,
                        "TIMER_MINUTES",
                        2,
                        "[" + sessionJson(1, 1, "IdpXx2gm90o", 0, 10) + "," + sessionJson(1, 1, "IdpXx2gm90o", 10, 20)
                                + "]"),
                missionBody(
                        today,
                        "TIMER_MINUTES",
                        11,
                        IntStream.rangeClosed(1, 11)
                                .mapToObj(i -> sessionJson(i, 1, "IdpXx2gm90o", 0, 10))
                                .collect(Collectors.joining(",", "[", "]"))),
                missionBody(today, "TIMER_MINUTES", 61, "[" + sessionJson(1, 61, "IdpXx2gm90o", 0, 10) + "]"),
                missionBody(today, "TIMER_MINUTES", 1, "[" + sessionJson(1, 1, "x?list=evil", 0, 10) + "]"),
                missionBody(today, "STEPS", 3000, "[" + sessionJson(1, 1, "IdpXx2gm90o", 0, 10) + "]"));
        for (String body : invalid) {
            mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                            .header(HttpHeaders.AUTHORIZATION, parent)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }
        // 목표 분이 칸 합(2+3)과 다르면 서버가 고쳐 넣지 않고 까닭을 적어 거절한다
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missionBody(today, "TIMER_MINUTES", 999, sessions)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("칸 시간의 합(5분)")));
        assertThat(jdbc.sql("select count(*) from missions where family_id = ?")
                        .param(familyId())
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);

        // 단건: 없는 미션 404, 다른 가족 403(목록과 같은 권한)
        mockMvc.perform(get("/api/v1/missions/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MISSION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/missions/" + missionId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(family.outsiderUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));

        // 동의를 거둔 아이를 참여자로 고르면 422
        ProfileSummary childSummary = Summaries.summary(family.child, today);
        ProfileSummary withdrawnChild = new ProfileSummary(
                childSummary.profileId(),
                childSummary.familyId(),
                childSummary.name(),
                childSummary.role(),
                childSummary.ageGroup(),
                childSummary.hasAccount(),
                childSummary.inviteStatus(),
                childSummary.supportMode(),
                false,
                true,
                false);
        given(profileQuery.summariesOfFamily(familyId()))
                .willReturn(List.of(
                        Summaries.summary(family.parent, today),
                        withdrawnChild,
                        Summaries.summary(family.cheerParent, today)));
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missionBody(today, "TIMER_MINUTES", 5, sessions)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    private String missionBody(LocalDate day, String metric, int targetValue, String sessionsJson) {
        return "{\"title\":\"거북이 스트레칭\",\"startDate\":\"" + day + "\",\"endDate\":\"" + day
                + "\",\"targetMetric\":\"" + metric + "\",\"targetValue\":" + targetValue
                + ",\"participantProfileIds\":[\"" + childId() + "\"],\"sessions\":" + sessionsJson + "}";
    }

    private static String sessionJson(int position, int minutes, String videoId, int startSec, int endSec) {
        return "{\"position\":" + position + ",\"phase\":\"MAIN\",\"title\":\"동작" + position
                + "\",\"minutes\":" + minutes + ",\"clip\":{\"videoId\":\"" + videoId + "\",\"startSec\":"
                + startSec + ",\"endSec\":" + endSec + "}}";
    }

    private static String extract(String regex, MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile(regex).matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) throw new IllegalStateException("응답에서 값을 찾지 못했다: " + regex);
        return matcher.group(1);
    }

    private void insertFamilyRows() {
        Instant now = Instant.now();
        jdbc.sql("insert into families (id, name, created_at, updated_at) values (?, ?, ?, ?)")
                .params(familyId(), "테스트가족", now, now)
                .update();
        for (ProfileDetails m : family.members()) {
            jdbc.sql(
                            "insert into users (id, provider, provider_user_id, status, created_at, updated_at) values (?, 'GOOGLE', ?, 'ACTIVE', ?, ?)")
                    .params(List.of(m.userId(), "sub-" + m.userId(), now, now))
                    .update();
            jdbc.sql("""
                            insert into profiles (id, family_id, user_id, display_name, birth_date, sex, role, is_owner, support_mode, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\
                            """)
                    .params(java.util.Arrays.asList(
                            m.profileId(),
                            familyId(),
                            m.userId(),
                            m.name(),
                            m.birthDate(),
                            m.sex().name(),
                            m.role().name(),
                            m == family.parent,
                            m.supportMode() == null ? null : m.supportMode().name(),
                            now,
                            now))
                    .update();
        }
    }
}
