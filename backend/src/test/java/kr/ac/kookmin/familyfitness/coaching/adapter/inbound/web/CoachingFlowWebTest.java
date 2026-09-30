package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity;
import kr.ac.kookmin.familyfitness.activity.api.VerifiedSummary;
import kr.ac.kookmin.familyfitness.coaching.application.AppTime;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunExecutorConfig;
import kr.ac.kookmin.familyfitness.coaching.application.CoachRunPipeline;
import kr.ac.kookmin.familyfitness.coaching.application.StaleCoachRunSweeper;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Runs;
import kr.ac.kookmin.familyfitness.coaching.support.Summaries;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H2 + Flyway(시드) 위에서 코치 실행 → 승인 → 미션 → 활동 → 영상 → 대화 → 주간 요약의 전체 흐름.
 * identity·fitness·activity 는 이 워크트리에 구현이 없으므로 공개 포트를 {@link MockitoBean} 으로 대신하고,
 * 편성 전용 스레드 풀은 동기 {@link SyncTaskExecutor} 로 바꿔 커밋 직후(AFTER_COMMIT) 결정적으로 끝나게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.coach.poll-interval-ms=0", "app.coach.max-polls=3"})
class CoachingFlowWebTest {
    @TestBean(name = CoachRunExecutorConfig.EXECUTOR, methodName = "syncCoachRunExecutor")
    TaskExecutor coachRunTaskExecutor;

    static TaskExecutor syncCoachRunExecutor() {
        return new SyncTaskExecutor();
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestAuth auth;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AppTime time;

    @Autowired
    CoachRunRepository coachRuns;

    @Autowired
    StaleCoachRunSweeper sweeper;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    CoachRunPipeline pipeline;

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
        given(familyAccess.requireActingAs(family.childUser, childId())).willReturn(childSummary);
        given(familyAccess.requireActingAs(family.parentUser, parentId())).willReturn(parentSummary);
        // 아이는 계정이 있다 — 보호자 계정이 아이 이름으로 하지 못한다
        given(familyAccess.requireActingAs(family.parentUser, childId())).willThrow(new CannotActAsProfileException());
        given(profileQuery.findSummary(childId())).willReturn(childSummary);
        given(profileQuery.findSummary(parentId())).willReturn(parentSummary);
        given(profileQuery.findDetails(childId())).willReturn(family.child);
        given(fitnessQuery.hasAnyTest(any())).willReturn(true);
        given(fitnessQuery.latestOf(childId()))
                .willReturn(new LatestFitness(
                        childId(),
                        UUID.randomUUID(),
                        today.minusDays(2),
                        new BigDecimal("140.5"),
                        new BigDecimal("35.0"),
                        null,
                        null,
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
        given(activityQuery.verifiedSummary(any())).willReturn(new VerifiedSummary(0, 0));
        given(cheerQuery.countCheers(eq(familyId()), any(), any())).willReturn(2);
    }

    /** FE 가 실제로 보내는 몸통(fe:src/lib/api/queries.ts PlanRequest + minutesPerSession). */
    private String planBody(UUID profileId, LocalDate date, boolean withParent) {
        return "{\"profileId\":\"" + profileId + "\",\"date\":\"" + date
                + "\",\"minutes\":20,\"quiet\":true,\"place\":\"HOME\",\"focusFactor\":null,\"withParent\":"
                + withParent + ",\"minutesPerSession\":20}";
    }

    private MvcResult startPlan(String bearer, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/families/" + familyId() + "/coach/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String statusOf(String runId) {
        return jdbc.sql("select status from coach_runs where id = ?")
                .param(UUID.fromString(runId))
                .query(String.class)
                .single();
    }

    @Test
    @DisplayName("코치 실행부터 주간 요약까지 한 흐름")
    void 코치_실행부터_주간_요약까지_한_흐름() throws Exception {
        String parent = auth.bearer(family.parentUser);
        String child = auth.bearer(family.childUser);

        LocalDate today = time.today();

        // 자녀 계정은 시작할 수 없다 → 403
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/coach/runs")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(childId(), today, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));

        // 시작(202) — 동기 실행기라 응답 시점에 이미 끝나 있다.
        MvcResult startResult = mockMvc.perform(post("/api/v1/families/" + familyId() + "/coach/runs")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(childId(), today, true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.pollAfterMs").value(1500))
                .andReturn();
        String runId = extract("\"coachRunId\":\"([^\"]+)\"", startResult);

        // 조회: 부모는 승인 가능, 그 아이의 그날 하루짜리 제안 하나, 참여자는 아이 + 요청한 보호자(동반자), 영상 제목은 V132 값.
        // 칸은 스텁의 20분 = 준비 2 · 본 4 · 정리 1 클립, 분은 1 · 1 · 5 · 4 · 4 · 4 · 1, 구간 제목은 V132 클립 표 값
        mockMvc.perform(get("/api/v1/coach/runs/" + runId).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.failureCode").value(nullValue()))
                .andExpect(jsonPath("$.notices", hasSize(0)))
                .andExpect(jsonPath("$.canApprove").value(true))
                .andExpect(jsonPath("$.missionCount").value(0))
                .andExpect(jsonPath("$.weekStart").value(time.thisWeekStart().toString()))
                .andExpect(jsonPath("$.profileId").value(childId().toString()))
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.steps", hasSize(4)))
                .andExpect(jsonPath("$.steps[0].name").value("assess"))
                .andExpect(jsonPath("$.proposals", hasSize(1)))
                .andExpect(jsonPath("$.proposals[0].title").value("유연성 키우기 20분"))
                .andExpect(jsonPath("$.proposals[0].startDate").value(today.toString()))
                .andExpect(jsonPath("$.proposals[0].endDate").value(today.toString()))
                .andExpect(jsonPath("$.proposals[0].targetMetric").value("TIMER_MINUTES"))
                .andExpect(jsonPath("$.proposals[0].targetValue").value(20))
                .andExpect(jsonPath("$.proposals[0].video.videoId").value("Eg3GpTv7z8s"))
                .andExpect(jsonPath("$.proposals[0].video.title").value("[👦🏻유소년] 성장기 학생들을 위한 근력 운동 프로그램 (30min)"))
                // 대표 영상은 첫 본운동 칸(3번 칸 「앉아서 상체숙여 양팔 등 뒤로 펴기」 500초)이다 — 준비운동 첫 칸(144초)이 아니다
                .andExpect(jsonPath("$.proposals[0].video.startSec").value(500))
                .andExpect(jsonPath("$.proposals[0].sessions[2].clip.startSec").value(500))
                .andExpect(jsonPath("$.proposals[0].participants", hasSize(2)))
                .andExpect(jsonPath("$.proposals[0].participants[0].profileId")
                        .value(childId().toString()))
                .andExpect(jsonPath("$.proposals[0].participants[1].profileId")
                        .value(parentId().toString()))
                .andExpect(jsonPath("$.proposals[0].participants[1].coachRole").value("동반자"))
                .andExpect(jsonPath("$.proposals[0].citations", hasSize(2)))
                .andExpect(jsonPath("$.proposals[0].citations[1].chunkId").value("video:Eg3GpTv7z8s"))
                .andExpect(jsonPath("$.proposals[0].sessions", hasSize(7)))
                .andExpect(jsonPath("$.proposals[0].sessions[*].position").value(contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.proposals[0].sessions[*].phase")
                        .value(contains("WARMUP", "WARMUP", "MAIN", "MAIN", "MAIN", "MAIN", "COOLDOWN")))
                .andExpect(jsonPath("$.proposals[0].sessions[*].minutes").value(contains(1, 1, 5, 4, 4, 4, 1)))
                .andExpect(jsonPath("$.proposals[0].sessions[0].title").value("넙다리 안쪽 늘리기 (나비자세)"))
                .andExpect(jsonPath("$.proposals[0].sessions[0].factor").value("유연성"))
                .andExpect(jsonPath("$.proposals[0].sessions[0].clip.videoId").value("Eg3GpTv7z8s"))
                .andExpect(jsonPath("$.proposals[0].sessions[0].clip.startSec").value(144))
                .andExpect(jsonPath("$.proposals[0].sessions[0].clip.endSec").value(182))
                .andExpect(jsonPath("$.proposals[0].sessions[0].clip.title").value("넙다리 안쪽 늘리기 (나비자세)"))
                .andExpect(jsonPath("$.proposals[0].sessions[0].completed").doesNotExist());
        assertThat(jdbc.sql(
                                "select count(*) from coach_run_proposal_sessions where coach_run_id = ? and item_position = 0")
                        .param(UUID.fromString(runId))
                        .query(Integer.class)
                        .single())
                .isEqualTo(7);
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
                // AI 자료에 영상 길이가 없어 V132 는 길이를 비워 둔다
                .andExpect(jsonPath("$.missions[0].video.durationSec", nullValue()))
                // 승인은 제안 칸을 차례 그대로 복사한다
                .andExpect(jsonPath("$.missions[0].targetValue").value(20))
                .andExpect(jsonPath("$.missions[0].sessions", hasSize(7)))
                .andExpect(jsonPath("$.missions[0].sessions[*].minutes").value(contains(1, 1, 5, 4, 4, 4, 1)))
                .andExpect(jsonPath("$.missions[0].sessions[0].clip.title").value("넙다리 안쪽 늘리기 (나비자세)"))
                .andExpect(jsonPath("$.missions[0].sessions[6].phase").value("COOLDOWN"))
                .andExpect(jsonPath("$.missions[0].participants", hasSize(2)))
                .andExpect(jsonPath("$.missions[0].participants[?(@.profileId=='" + childId() + "')].name")
                        .value("민준"))
                .andExpect(jsonPath("$.missions[0].participants[?(@.profileId=='" + childId() + "')].progress")
                        .value(0.0))
                .andExpect(jsonPath("$.missions[0].participants[*].doneSessions[*]", hasSize(0)));

        // 칸 끝: 첫 칸(1분)을 끝내면 1/20 · +5. 같이 하기로 한 보호자(동반자)에게도 번진다. 같은 칸을 다시 보내면 0
        completeSession(child, missionId, 1, 60)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.verifiedBy").value("VIDEO_PROGRESS"))
                .andExpect(jsonPath("$.missionProgress").value(0.05))
                .andExpect(jsonPath("$.missionCompleted").value(false))
                .andExpect(jsonPath("$.xpGained").value(5));
        completeSession(child, missionId, 1, 60)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xpGained").value(0));
        verify(activityRecorder).addActiveSeconds(childId(), time.today(), ActivitySource.VIDEO, 60);
        verify(activityRecorder).addActiveSeconds(parentId(), time.today(), ActivitySource.VIDEO, 60);

        // 타이머: 경과 20분으로 자르고 활동은 쌓지만, 칸 있는 미션은 활동 합계(모의 45분)가 아니라 끝낸 칸으로만 센다
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
                .andExpect(jsonPath("$.missionProgress").value(0.05))
                .andExpect(jsonPath("$.missionCompleted").value(false));
        verify(activityRecorder).addActiveMinutes(childId(), time.today(), ActivitySource.TIMER, 20);

        // 남은 칸(1 · 5 · 4 · 4 · 4 · 1분)을 다 끝내면 완료 · 마지막 칸은 +5 +20
        int[] minutes = {1, 1, 5, 4, 4, 4, 1};
        for (int seq = 2; seq <= 6; seq++) {
            completeSession(child, missionId, seq, minutes[seq - 1] * 60)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.missionCompleted").value(false))
                    .andExpect(jsonPath("$.xpGained").value(5));
        }
        completeSession(child, missionId, 7, 60)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionProgress").value(1.0))
                .andExpect(jsonPath("$.missionCompleted").value(true))
                .andExpect(jsonPath("$.xpGained").value(25));
        mockMvc.perform(get("/api/v1/missions/" + missionId).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[?(@.profileId=='" + childId() + "')].doneSessions[*]")
                        .value(contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.participants[?(@.profileId=='" + parentId() + "')].doneSessions[*]")
                        .value(contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.participants[?(@.profileId=='" + parentId() + "')].completed")
                        .value(true));

        // 영상 진행률: 완주로 본다. 적립 분은 영상 길이에서 오는데 V132 영상은 길이가 없어 0분이다
        mockMvc.perform(post("/api/v1/videos/IdpXx2gm90o/progress")
                        .header(HttpHeaders.AUTHORIZATION, child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"progress\":0.95,\"watchedSec\":570}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxProgress").value(0.95))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.creditedMinutes").value(0))
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
                .andExpect(jsonPath("$.citations[0].sourceLabel").value("국민체력100 운동처방, 유소년 11세"))
                .andExpect(jsonPath("$.citations[0].excerpt").value("국민체력100 운동처방, 유소년 11세"))
                .andReturn();
        String conversationId = extract("\"conversationId\":\"([^\"]+)\"", chatResult);
        // 계정 있는 아이 이름으로는 그 아이 계정만 묻는다 — 보호자 계정은 403
        mockMvc.perform(post("/api/v1/coach/chat")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + childId() + "\",\"conversationId\":\"" + conversationId
                                + "\",\"question\":\"무릎 통증이 있어요\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/coach/chat")
                        .header(HttpHeaders.AUTHORIZATION, child)
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
                .andExpect(jsonPath("$.summary").value("유연성은 매일 조금씩 늘려 가는 영역입니다. 오늘 20분이면 충분합니다."))
                .andExpect(jsonPath("$.missionStats.total").value(1))
                // 아이가 끝낸 칸이 동반자 보호자에게도 번져 참여자 둘 다 완료다
                .andExpect(jsonPath("$.missionStats.completed").value(1))
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
        mockMvc.perform(post("/api/v1/missions/" + missionId + "/participants/" + parentId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARTICIPANT"));

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

        // 영상 목록: 유소년 안전 필터 + 요인. videoId 차례라 공단 영상(V161, 0AUDLJ08S_…)이 먼저 나온다 — url 은 mp4 주소다
        mockMvc.perform(get("/api/v1/videos?ageGroup=유소년&factor=근력&size=1").header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos", hasSize(1)))
                .andExpect(jsonPath("$.videos[0].videoId").value("0AUDLJ08S_00351"))
                .andExpect(jsonPath("$.videos[0].title").value("팔굽혀펴기"))
                .andExpect(
                        jsonPath("$.videos[0].url").value("https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00351.mp4"))
                .andExpect(jsonPath("$.videos[0].mediaUrl")
                        .value("https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00351.mp4"))
                .andExpect(jsonPath("$.videos[0].thumbnailUrl")
                        .value("https://openapi.kspo.or.kr/web/image/0AUDLJ08S_00351/0AUDLJ08S_00351_SC_00002.jpeg"))
                .andExpect(jsonPath("$.videos[0].durationSec").value(91))
                .andExpect(jsonPath("$.videos[0].label.factors[0]").value("근력"));
        // 유튜브 영상(V132): 근력 영상 중 유아기(IfV5H7USgaA) · 성인(IhShIA-WJNE 등)은 빠진다. mediaUrl 은 null 이다
        mockMvc.perform(get("/api/v1/videos?ageGroup=유소년&factor=근력&size=1&cursor=0AUDLJ08S_99999")
                        .header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos", hasSize(1)))
                .andExpect(jsonPath("$.videos[0].videoId").value("Eg3GpTv7z8s"))
                .andExpect(jsonPath("$.videos[0].url").value("https://www.youtube.com/watch?v=Eg3GpTv7z8s"))
                .andExpect(jsonPath("$.videos[0].mediaUrl", nullValue()))
                .andExpect(
                        jsonPath("$.videos[0].thumbnailUrl").value("https://i.ytimg.com/vi/Eg3GpTv7z8s/hqdefault.jpg"))
                .andExpect(jsonPath("$.videos[0].label.factors[0]").value("근력"))
                .andExpect(jsonPath("$.videos[0].favorited").value(false))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
        // 어르신: 공단 어르신 영상은 V164 부터 싣지 않는다. 성인(공통) 영상을 똑같이 받는다.
        // 「공통」 영상은 V165 부터 청소년 · 성인 두 연령대라 연령 범위가 13 ~ 64세다 — 청소년 목록에도 같은 영상이 나온다
        for (String ageGroup : List.of("어르신", "청소년")) {
            mockMvc.perform(get("/api/v1/videos?size=1&ageGroup=" + ageGroup).header(HttpHeaders.AUTHORIZATION, child))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.videos", hasSize(1)))
                    .andExpect(jsonPath("$.videos[0].videoId").value("0AUDLJ08S_00181"))
                    .andExpect(jsonPath("$.videos[0].label.ageFrom").value(13))
                    .andExpect(jsonPath("$.videos[0].label.ageTo").value(64));
        }
        // 커서: videoId 오름차순으로 다음 유소년 영상
        mockMvc.perform(get("/api/v1/videos?ageGroup=유소년&size=1&cursor=Eg3GpTv7z8s")
                        .header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos[0].videoId").value("HXS4NM82zd0"))
                .andExpect(jsonPath("$.nextCursor").value("HXS4NM82zd0"));
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
    @DisplayName("멈춘 RUNNING(기준 15초를 넘김)은 그 (프로필, 날짜)의 새 편성을 막지 않고 FAILED 가 되며, 정리 작업은 다른 멈춘 실행도 FAILED 로 바꾸고 잠금을 푼다")
    void 멈춘_RUNNING_은_새_편성을_막지_않고_정리_작업이_FAILED_로_바꾼다() throws Exception {
        LocalDate today = time.today();
        CoachRun stuckToday = Runs.running(
                familyId(), childId(), today, parentId(), time.now().minusSeconds(600));
        CoachRun stuckTomorrow = Runs.running(
                familyId(), childId(), today.plusDays(1), parentId(), time.now().minusSeconds(600));
        tx.executeWithoutResult(status -> {
            coachRuns.save(stuckToday);
            coachRuns.save(stuckTomorrow);
        });

        assertThat(startPlan(auth.bearer(family.parentUser), planBody(childId(), today, false))
                        .getResponse()
                        .getStatus())
                .isEqualTo(202);
        assertThat(statusOf(stuckToday.getId().toString())).isEqualTo("FAILED");

        assertThat(sweeper.sweep()).isPositive();
        assertThat(statusOf(stuckTomorrow.getId().toString())).isEqualTo("FAILED");
        assertThat(jdbc.sql("select failure_reason from coach_runs where id = ?")
                        .param(stuckTomorrow.getId())
                        .query(String.class)
                        .single())
                .startsWith("stale: 15초");
        assertThat(jdbc.sql("select failure_code from coach_runs where id in (?, ?)")
                        .param(stuckToday.getId())
                        .param(stuckTomorrow.getId())
                        .query(String.class)
                        .list())
                .containsExactly("STALE", "STALE");
        mockMvc.perform(get("/api/v1/coach/runs/" + stuckTomorrow.getId())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(family.parentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("STALE"))
                .andExpect(jsonPath("$.notices", hasSize(0)));
        assertThat(jdbc.sql("select count(*) from coach_runs where family_id = ? and lock_key is not null")
                        .param(familyId())
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("파이프라인이 RUNNING 을 읽은 뒤 정리 작업이 FAILED 로 먼저 커밋하면, 늦게 온 AI 결과는 버리고 FAILED · 잠금 해제를 그대로 둔다")
    void 정리_작업이_먼저_끝낸_실행을_늦게_온_AI_결과가_되살리지_않는다() {
        CoachRun stuck = Runs.running(
                familyId(), childId(), time.today(), parentId(), time.now().minusSeconds(600));
        tx.executeWithoutResult(
                status -> assertThat(coachRuns.insertRunning(stuck)).isTrue());
        // complete() 는 실행을 읽은 뒤 대상 프로필을 조회한다. 바로 그때 정리 작업이 다른 트랜잭션으로 커밋된다.
        given(profileQuery.findDetails(childId())).willAnswer(inv -> {
            commitSeparately(sweeper::sweep);
            return family.child;
        });

        pipeline.complete(stuck.getId(), succeeded());

        assertThat(statusOf(stuck.getId().toString())).isEqualTo("FAILED");
        assertThat(jdbc.sql("select failure_reason from coach_runs where id = ?")
                        .param(stuck.getId())
                        .query(String.class)
                        .optional())
                .hasValueSatisfying(reason -> assertThat(reason).startsWith("stale:"));
        assertThat(lockKeyOf(stuck.getId())).isNull();
        assertThat(jdbc.sql("select count(*) from coach_run_proposal_items where coach_run_id = ?")
                        .param(stuck.getId())
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("정리 작업이 FAILED 로 바꾼 뒤 늦게 온 AI 접수 기록은 RUNNING 과 (프로필, 날짜) 잠금을 되살리지 않는다")
    void 늦게_온_AI_접수_기록은_RUNNING_과_잠금을_되살리지_않는다() {
        CoachRun stuck = Runs.running(
                familyId(),
                childId(),
                time.today().plusDays(1),
                parentId(),
                time.now().minusSeconds(600));
        tx.executeWithoutResult(
                status -> assertThat(coachRuns.insertRunning(stuck)).isTrue());
        CoachRun read = tx.execute(status -> coachRuns.findById(stuck.getId())); // 파이프라인이 읽어 둔 RUNNING
        sweeper.sweep();

        read.attachAiRun("cr_late", time.now());
        tx.executeWithoutResult(
                status -> assertThat(coachRuns.attachAiRunIfRunning(read)).isFalse());

        assertThat(statusOf(stuck.getId().toString())).isEqualTo("FAILED");
        assertThat(lockKeyOf(stuck.getId())).isNull();
        assertThat(jdbc.sql("select ai_run_id from coach_runs where id = ?")
                        .param(stuck.getId())
                        .query(String.class)
                        .optional())
                .isEmpty();
    }

    private void commitSeparately(Runnable work) {
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        separate.executeWithoutResult(status -> work.run());
    }

    private @Nullable String lockKeyOf(UUID runId) {
        return jdbc.sql("select lock_key from coach_runs where id = ?")
                .param(runId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /** 대상 아이 한 명의 하루짜리 succeeded 결과. */
    private CoachRunResult succeeded() {
        CoachRunResult.Session session = new CoachRunResult.Session(
                0, "본운동", 1, "운동", "유연성", 60, new CoachRunResult.Video("IdpXx2gm90o", 96, 156), List.of(1));
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                "일간",
                "오늘",
                time.today().toString(),
                time.today().toString(),
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(childId()), "주행자")),
                20,
                60,
                List.of(session),
                "아이",
                "부모",
                "이유 [1].");
        return new CoachRunResult(
                "cr_late",
                "succeeded",
                List.of(),
                new CoachRunResult.Proposal(List.of(mission), List.of(new Citation(1, "처방", "p:1", null)), List.of()),
                false,
                null);
    }

    @Test
    @DisplayName("요청 몸통 검증: profileId · date · minutes 가 없거나(옛 주간 몸통 포함) 모르는 힘이면 400, 지난 날짜면 422 INVALID_DATE")
    void 요청_몸통_검증() throws Exception {
        String parent = auth.bearer(family.parentUser);
        LocalDate today = time.today();

        assertThat(startPlan(parent, "{\"daysPerWeek\":3,\"minutesPerSession\":15}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        assertThat(startPlan(parent, "{\"profileId\":\"" + childId() + "\",\"date\":\"" + today + "\"}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        assertThat(startPlan(parent, planBody(childId(), today, false).replace("\"minutes\":20", "\"minutes\":3"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        assertThat(startPlan(parent, planBody(childId(), today, false).replace("null", "\"없는힘\""))
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/coach/runs")
                        .header(HttpHeaders.AUTHORIZATION, parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(childId(), today.minusDays(1), false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
        assertThat(jdbc.sql("select count(*) from coach_runs where family_id = ?")
                        .param(familyId())
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("같은 아이 · 같은 날 다시 짜면 기다리던 제안은 REJECTED(새 제안으로 바뀌었어요), latest?profileId= 는 그 아이의 새 실행을 준다")
    void 같은_아이_같은_날_다시_짜면_기다리던_제안은_거절되고_latest_는_새_실행을_준다() throws Exception {
        String parent = auth.bearer(family.parentUser);
        LocalDate today = time.today();
        String first = extract("\"coachRunId\":\"([^\"]+)\"", startPlan(parent, planBody(childId(), today, false)));
        assertThat(statusOf(first)).isEqualTo("AWAITING_APPROVAL");

        String second = extract("\"coachRunId\":\"([^\"]+)\"", startPlan(parent, planBody(childId(), today, false)));

        assertThat(statusOf(first)).isEqualTo("REJECTED");
        assertThat(jdbc.sql("select rejected_reason from coach_runs where id = ?")
                        .param(UUID.fromString(first))
                        .query(String.class)
                        .single())
                .isEqualTo("새 제안으로 바뀌었어요");
        assertThat(statusOf(second)).isEqualTo("AWAITING_APPROVAL");
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/coach/runs/latest?profileId=" + childId())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(family.childUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachRunId").value(second))
                .andExpect(jsonPath("$.profileId").value(childId().toString()))
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.canApprove").value(false))
                .andExpect(jsonPath("$.proposals[0].participants", hasSize(1)));
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/coach/runs/latest")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachRunId").value(second))
                .andExpect(jsonPath("$.canApprove").value(true));
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/coach/runs/latest?profileId=" + parentId())
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COACH_RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("승인한 코치 미션을 모두 지운 회차(APPROVED · 미션 0)는 latest 에서 건너뛴다 — 그 회차 단건 조회는 그대로다")
    void 승인한_미션을_모두_지운_회차는_latest_에서_건너뛴다() throws Exception {
        String parent = auth.bearer(family.parentUser);
        String runId =
                extract("\"coachRunId\":\"([^\"]+)\"", startPlan(parent, planBody(childId(), time.today(), false)));
        MvcResult approved = mockMvc.perform(
                        post("/api/v1/coach/runs/" + runId + "/approve").header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andReturn();
        String missionId = extract("\"missionId\":\"([^\"]+)\"", approved);
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/coach/runs/latest?profileId=" + childId())
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachRunId").value(runId))
                .andExpect(jsonPath("$.missionCount").value(1));

        mockMvc.perform(delete("/api/v1/missions/" + missionId).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/" + familyId() + "/coach/runs/latest?profileId=" + childId())
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COACH_RUN_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/coach/runs/latest")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/coach/runs/" + runId).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.missionCount").value(0));
    }

    @Test
    @DisplayName("(프로필, 날짜) 잠금은 DB 유니크 인덱스다 — 같은 키의 두 번째 RUNNING 은 들어가지 않고, 요청은 409 RUN_IN_PROGRESS")
    void 프로필_날짜_잠금은_DB_유니크_인덱스다() throws Exception {
        LocalDate tomorrow = time.today().plusDays(1);
        tx.executeWithoutResult(status -> assertThat(
                        coachRuns.insertRunning(Runs.running(familyId(), childId(), tomorrow, parentId(), time.now())))
                .isTrue());

        Boolean second = tx.execute(status -> {
            boolean inserted =
                    coachRuns.insertRunning(Runs.running(familyId(), childId(), tomorrow, parentId(), time.now()));
            status.setRollbackOnly();
            return inserted;
        });

        assertThat(second).isFalse();
        mockMvc.perform(post("/api/v1/families/" + familyId() + "/coach/runs")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(family.parentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(childId(), tomorrow, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RUN_IN_PROGRESS"));
        assertThat(jdbc.sql("select count(*) from coach_runs where family_id = ? and status = 'RUNNING'")
                        .param(familyId())
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("BE 영상 표에 없는 AI 클립 영상도 제안 · 미션에 그대로 저장되고 유튜브 주소로 나간다")
    void 영상_표에_없는_AI_클립_영상도_그대로_저장되고_유튜브_주소로_나간다() throws Exception {
        String parent = auth.bearer(family.parentUser);
        // 영상 표(exercise_videos)에 없는 id — 다음 AI 릴리스에서 새로 생길 영상처럼.
        String videoId = "notInTable1";
        UUID runId = UUID.randomUUID();
        CoachProposalItem item = new CoachProposalItem(
                0,
                "월요일 늘이기",
                "TIMER_MINUTES",
                15,
                "또래 처방에 나온 늘이는 동작을 앞세워 골랐습니다 [1].",
                "준비운동 넙다리 안쪽 늘리기 (나비자세)",
                time.today(),
                time.today(),
                List.of(new ProposalParticipant(childId(), ProfileRole.CHILD, "주행자")),
                new ProposalVideo(videoId, 144),
                List.of(),
                "아이 문구",
                "부모 문구");
        tx.executeWithoutResult(status -> coachRuns.save(CoachRun.awaitingApproval(
                runId, familyId(), List.of(item), time.thisWeekStart(), List.of(), null, 1, 15, time.now())));

        mockMvc.perform(get("/api/v1/coach/runs/" + runId).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposals[0].video.videoId").value(videoId))
                .andExpect(jsonPath("$.proposals[0].video.title", nullValue()))
                .andExpect(jsonPath("$.proposals[0].video.url").value("https://www.youtube.com/watch?v=" + videoId));
        mockMvc.perform(post("/api/v1/coach/runs/" + runId + "/approve").header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdMissions", hasSize(1)));
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/missions").header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions[?(@.coachRunId=='" + runId + "')].video.videoId")
                        .value(videoId))
                .andExpect(jsonPath("$.missions[?(@.coachRunId=='" + runId + "')].video.url")
                        .value("https://www.youtube.com/watch?v=" + videoId));
    }

    @Test
    @DisplayName("AI 가 칸 요인을 빈 문자열로 보내면 제안 · 미션 칸의 factor 는 null 로 나가고, 공단 영상 칸은 mp4 · 첫 장면 주소와 AI 인용을 그대로 싣는다")
    void 빈_요인_칸은_factor_null_로_나가고_공단_영상_칸은_mp4_로_나간다() throws Exception {
        String parent = auth.bearer(family.parentUser);
        CoachRun run = Runs.running(familyId(), childId(), time.today(), parentId(), time.now());
        tx.executeWithoutResult(
                status -> assertThat(coachRuns.insertRunning(run)).isTrue());
        // AI 명세 82b3614 · 담당자 코드 모양: 요인 라벨이 없는 클립이고 대상 요인도 없으면 fitness_factor 는 "", 공단 영상은 url 이 mp4 다
        String mp4 = "https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00234.mp4";
        CoachRunResult.Session session = new CoachRunResult.Session(
                0,
                "본운동",
                1,
                "누워서 배가로근 수축1",
                "",
                60,
                new CoachRunResult.Video("0AUDLJ08S_00234", 0, 60, CoachRunResult.Video.SOURCE_KSPO, mp4),
                List.of(1));
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                "일간",
                "코어 깨우기",
                time.today().toString(),
                time.today().toString(),
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(childId()), "주행자")),
                10,
                60,
                List.of(session),
                "아이",
                "부모",
                "배 속 근육을 깨웁니다 [1].");
        pipeline.complete(
                run.getId(),
                new CoachRunResult(
                        "cr_empty_factor",
                        "succeeded",
                        List.of(),
                        new CoachRunResult.Proposal(
                                List.of(mission),
                                List.of(new Citation(
                                        1, "국민체력100 운동처방동영상 · 누워서 배가로근 수축 I", "kspo:0AUDLJ08S_00234", mp4)),
                                List.of()),
                        false,
                        null));

        mockMvc.perform(get("/api/v1/coach/runs/" + run.getId()).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.proposals[0].sessions", hasSize(1)))
                .andExpect(jsonPath("$.proposals[0].sessions[0].title").value("누워서 배가로근 수축1"))
                .andExpect(jsonPath("$.proposals[0].sessions[0].factor").value(nullValue()))
                .andExpect(jsonPath("$.proposals[0].sessions[0].clip.videoId").value("0AUDLJ08S_00234"))
                .andExpect(jsonPath("$.proposals[0].sessions[0].clip.mediaUrl").value(mp4))
                .andExpect(jsonPath("$.proposals[0].sessions[0].clip.thumbnailUrl")
                        .value(startsWith("https://openapi.kspo.or.kr/web/image/0AUDLJ08S_00234/")))
                .andExpect(jsonPath("$.proposals[0].video.url").value(mp4))
                .andExpect(jsonPath("$.proposals[0].citations[0].chunkId").value("kspo:0AUDLJ08S_00234"))
                .andExpect(jsonPath("$.proposals[0].citations[0].label").value("국민체력100 운동처방동영상 · 누워서 배가로근 수축 I"))
                .andExpect(jsonPath("$.proposals[0].citations[0].url").value(mp4));
        mockMvc.perform(post("/api/v1/coach/runs/" + run.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/families/" + familyId() + "/missions").header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions[?(@.coachRunId=='" + run.getId() + "')].sessions[0].factor")
                        .value(contains((Object) null)))
                .andExpect(jsonPath("$.missions[?(@.coachRunId=='" + run.getId() + "')].sessions[0].clip.mediaUrl")
                        .value(contains(mp4)));
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
                childSummary.sex(),
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

    @Test
    @DisplayName(
            "미션 날짜 규칙과 지우기 · 느낌 — 지난 날짜 422 · 제목 51자 400 · dates[] 한 트랜잭션 · DELETE 204/403/409 · feedback 204 덮어쓰기")
    void 미션_날짜_규칙과_지우기_느낌() throws Exception {
        String parent = auth.bearer(family.parentUser);
        String child = auth.bearer(family.childUser);
        LocalDate today = time.today();
        String oneSession = "[" + sessionJson(1, 1, "IdpXx2gm90o", 0, 10) + "]";

        // 제목 상한 · 여러 날 상한이 Swagger(OpenAPI) 문서에 실린다(MS-15)
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CreateMissionRequest.properties.title.maxLength")
                        .value(50))
                .andExpect(jsonPath("$.components.schemas.CreateMissionRequest.properties.dates.maxItems")
                        .value(28))
                // 칸 없는 분 목표 상한(360분, SA-11)도 설명에 실린다
                .andExpect(jsonPath("$.components.schemas.CreateMissionRequest.properties.targetValue.description")
                        .value(containsString("360")));

        // 지난 날짜 422 INVALID_DATE, 제목 51자 400 — 아무것도 저장하지 않는다
        postMission(parent, missionBody(today.minusDays(1), "TIMER_MINUTES", 1, oneSession))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
        postMission(parent, missionBody(today, "TIMER_MINUTES", 1, oneSession).replace("거북이 스트레칭", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // dates[]: 같은 날은 한 번, 날짜 차례로 하루짜리 한 건씩. 맨 위 missionId 는 첫 날 것
        MvcResult multi = postMission(parent, datesBody("", today.plusDays(2), today, today))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.missions", hasSize(2)))
                .andExpect(jsonPath("$.missions[0].startDate").value(today.toString()))
                .andExpect(jsonPath("$.missions[0].endDate").value(today.toString()))
                .andExpect(jsonPath("$.missions[1].startDate")
                        .value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.missions[1].endDate")
                        .value(today.plusDays(2).toString()))
                .andReturn();
        String first = extract("\"missions\":\\[\\{\"missionId\":\"([^\"]+)\"", multi);
        assertThat(extract("^\\{\"missionId\":\"([^\"]+)\"", multi)).isEqualTo(first);
        assertThat(missionCount()).isEqualTo(2);

        // 하나라도 지난 날이면 422 이고 앞날도 만들지 않는다. 날짜 칸을 섞거나 29일이면 400
        postMission(parent, datesBody("", today.plusDays(1), today.minusDays(1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
        postMission(parent, datesBody("\"startDate\":\"" + today + "\",", today))
                .andExpect(status().isBadRequest());
        postMission(
                        parent,
                        datesBody(
                                "",
                                IntStream.range(0, 29).mapToObj(today::plusDays).toArray(LocalDate[]::new)))
                .andExpect(status().isBadRequest());
        assertThat(missionCount()).isEqualTo(2);

        // 느낌: 참여자(아이)가 보내면 204, 다시 보내면 덮어쓴다. 모르는 느낌은 400
        postFeedback(child, first, "HARD").andExpect(status().isNoContent());
        postFeedback(child, first, "GOOD").andExpect(status().isNoContent());
        postFeedback(child, first, "SO_SO").andExpect(status().isBadRequest());
        assertThat(jdbc.sql("select feel from mission_feedback where mission_id = ?")
                        .param(UUID.fromString(first))
                        .query(String.class)
                        .list())
                .containsExactly("GOOD");

        // 지우기: 자녀 계정 403, 보호자 204 — 참여자 · 칸 · 느낌 행도 같이 지워지고 단건은 404
        mockMvc.perform(delete("/api/v1/missions/" + first).header(HttpHeaders.AUTHORIZATION, child))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"));
        mockMvc.perform(delete("/api/v1/missions/" + first).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/missions/" + first).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isNotFound());
        for (String table : List.of("mission_participants", "mission_sessions", "mission_feedback")) {
            assertThat(jdbc.sql("select count(*) from " + table + " where mission_id = ?")
                            .param(UUID.fromString(first))
                            .query(Integer.class)
                            .single())
                    .isZero();
        }

        // 아이가 칸을 끝낸 오늘 미션은 409 MISSION_ALREADY_STARTED
        String started = extract(
                "\"missionId\":\"([^\"]+)\"",
                postMission(parent, missionBody(today, "TIMER_MINUTES", 1, oneSession))
                        .andExpect(status().isCreated())
                        .andReturn());
        completeSession(child, started, 1, 60).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/missions/" + started).header(HttpHeaders.AUTHORIZATION, parent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MISSION_ALREADY_STARTED"));
    }

    private ResultActions postMission(String bearer, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/families/" + familyId() + "/missions")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions postFeedback(String bearer, String missionId, String feel) throws Exception {
        return mockMvc.perform(post("/api/v1/missions/" + missionId + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + childId() + "\",\"feel\":\"" + feel + "\"}"));
    }

    /** dates[] 로 보내는 1분 한 칸 미션. {@code extra} 는 그대로 끼워 넣는 칸(예: startDate). */
    private String datesBody(String extra, LocalDate... days) {
        String dates =
                java.util.Arrays.stream(days).map(it -> "\"" + it + "\"").collect(Collectors.joining(",", "[", "]"));
        return "{\"title\":\"거북이 스트레칭\"," + extra + "\"dates\":" + dates
                + ",\"targetMetric\":\"TIMER_MINUTES\",\"targetValue\":1,\"participantProfileIds\":[\"" + childId()
                + "\"],\"sessions\":[" + sessionJson(1, 1, "IdpXx2gm90o", 0, 10) + "]}";
    }

    private int missionCount() {
        return jdbc.sql("select count(*) from missions where family_id = ?")
                .param(familyId())
                .query(Integer.class)
                .single();
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

    /** 운동 한 칸 끝 — 아이 이름으로, 기기 시각 간격은 넉넉하게. */
    private ResultActions completeSession(String bearer, String missionId, int seq, int activeSeconds)
            throws Exception {
        Instant endedAt = Instant.now();
        return mockMvc.perform(post("/api/v1/missions/" + missionId + "/sessions/" + seq + "/complete")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + childId() + "\",\"activeSeconds\":" + activeSeconds
                        + ",\"startedAt\":\"" + endedAt.minusSeconds(3600) + "\",\"endedAt\":\"" + endedAt
                        + "\"}"));
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
