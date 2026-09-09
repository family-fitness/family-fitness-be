package kr.ac.kookmin.familyfitness.coaching.adapter.web

import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity
import kr.ac.kookmin.familyfitness.coaching.application.AppTime
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.MockitoKt.any
import kr.ac.kookmin.familyfitness.coaching.support.MockitoKt.eq
import kr.ac.kookmin.familyfitness.coaching.support.summary
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.support.TestAuth
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

/**
 * H2 + Flyway(시드) 위에서 코치 실행 → 승인 → 미션 → 활동 → 영상 → 대화 → 주간 요약의 전체 흐름.
 * identity·fitness·activity 는 이 워크트리에 구현이 없으므로 공개 포트를 [MockitoBean] 으로 대신하고,
 * 비동기 실행기는 동기 [SyncTaskExecutor] 로 바꿔 커밋 직후(AFTER_COMMIT) 결정적으로 끝나게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CoachingFlowWebTest.SyncAsync::class)
@TestPropertySource(properties = ["app.coach.poll-interval-ms=0", "app.coach.max-polls=3"])
class CoachingFlowWebTest {
    @TestConfiguration(proxyBeanMethods = false)
    class SyncAsync : AsyncConfigurer {
        override fun getAsyncExecutor(): Executor = SyncTaskExecutor()
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var auth: TestAuth

    @Autowired lateinit var jdbc: JdbcClient

    @Autowired lateinit var time: AppTime

    @MockitoBean lateinit var profileQuery: ProfileQuery

    @MockitoBean lateinit var familyAccess: FamilyAccess

    @MockitoBean lateinit var cheerQuery: CheerQuery

    @MockitoBean lateinit var fitnessQuery: FitnessQuery

    @MockitoBean lateinit var activityRecorder: ActivityRecorder

    @MockitoBean lateinit var activityQuery: ActivityQuery

    private val family = Family()
    private val familyId get() = family.familyId
    private val childId get() = family.child.profileId
    private val parentId get() = family.parent.profileId
    private var childTotals = ActivityTotals(0, 0, 0)

    @BeforeEach
    fun setUp() {
        reset(profileQuery, familyAccess, cheerQuery, fitnessQuery, activityRecorder, activityQuery)
        insertFamilyRows()
        val today = time.today()
        val parentSummary = family.parent.summary(today)
        val childSummary = family.child.summary(today)
        val cheerSummary = family.cheerParent.summary(today)

        given(profileQuery.summariesOfFamily(familyId)).willReturn(listOf(parentSummary, childSummary, cheerSummary))
        given(profileQuery.detailsOfFamily(familyId)).willReturn(family.members)
        given(familyAccess.requireMember(family.parentUser, familyId)).willReturn(parentSummary)
        given(familyAccess.requireMember(family.childUser, familyId)).willReturn(childSummary)
        given(familyAccess.requireMember(eq(family.outsiderUser), any())).willThrow(NotSameFamilyException())
        given(familyAccess.requireParent(family.parentUser, familyId)).willReturn(parentSummary)
        given(familyAccess.requireParent(family.childUser, familyId)).willThrow(NotAParentException())
        given(familyAccess.requireSameFamilyAsProfile(any(), eq(childId))).willReturn(childSummary)
        given(familyAccess.requireSameFamilyAsProfile(any(), eq(parentId))).willReturn(parentSummary)
        given(fitnessQuery.hasAnyTest(any())).willReturn(true)
        given(fitnessQuery.latestOf(childId)).willReturn(
            LatestFitness(
                childId,
                UUID.randomUUID(),
                today.minusDays(2),
                BigDecimal("140.5"),
                BigDecimal("35.0"),
                mapOf("012" to BigDecimal("8.0")),
                null,
                null,
            ),
        )
        given(activityRecorder.addActiveMinutes(any(), any(), any(), anyInt())).willAnswer { inv ->
            DailyActivity(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), 0, inv.getArgument(3))
        }
        given(activityRecorder.overwriteSteps(any(), any(), anyInt())).willAnswer { inv ->
            DailyActivity(inv.getArgument(0), inv.getArgument(1), ActivitySource.MANUAL, inv.getArgument(2), 0)
        }
        given(activityQuery.totals(eq(childId), any(), any())).willAnswer { childTotals }
        given(activityQuery.totals(eq(parentId), any(), any())).willReturn(ActivityTotals(0, 0, 0))
        given(activityQuery.totals(eq(family.cheerParent.profileId), any(), any())).willReturn(ActivityTotals(0, 0, 0))
        given(activityQuery.activeMinutesOn(eq(childId), any())).willReturn(20)
        given(cheerQuery.countCheers(eq(familyId), any(), any())).willReturn(2)
    }

    @Test
    fun `코치 실행부터 주간 요약까지 한 흐름`() {
        val parent = auth.bearer(family.parentUser)
        val child = auth.bearer(family.childUser)

        // 시작(202) — 동기 실행기라 응답 시점에 이미 끝나 있다.
        val runId =
            mockMvc
                .post("/api/v1/families/$familyId/coach/runs") {
                    header(HttpHeaders.AUTHORIZATION, parent)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"daysPerWeek":3,"minutesPerSession":15}"""
                }.andExpect {
                    status { isAccepted() }
                    jsonPath("$.status") { value("RUNNING") }
                    jsonPath("$.pollAfterMs") { value(1500) }
                }.andReturn()
                .let { Regex("\"coachRunId\":\"([^\"]+)\"").find(it.response.contentAsString)!!.groupValues[1] }

        // 같은 주 재시작 → 409
        mockMvc
            .post("/api/v1/families/$familyId/coach/runs") { header(HttpHeaders.AUTHORIZATION, parent) }
            .andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("ALREADY_RUN_THIS_WEEK") }
            }

        // 조회: 부모는 승인 가능, 제안에 시드 영상 제목·배지
        mockMvc
            .get("/api/v1/coach/runs/$runId") { header(HttpHeaders.AUTHORIZATION, parent) }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("AWAITING_APPROVAL") }
                jsonPath("$.canApprove") { value(true) }
                jsonPath("$.missionCount") { value(0) }
                jsonPath("$.weekStart") { value(time.thisWeekStart().toString()) }
                jsonPath("$.steps", hasSize<Any>(4))
                jsonPath("$.steps[0].name") { value("assess") }
                jsonPath("$.proposals", hasSize<Any>(1))
                jsonPath("$.proposals[0].targetMetric") { value("TIMER_MINUTES") }
                jsonPath("$.proposals[0].targetValue") { value(45) }
                jsonPath("$.proposals[0].video.videoId") { value("IdpXx2gm90o") }
                jsonPath("$.proposals[0].video.title") { value("초등학생의 기초체력향상과 운동능력발달을 위한 운동") }
                jsonPath("$.proposals[0].video.startSec") { value(96) }
                jsonPath("$.proposals[0].video.badges[0]") { value("조용함") }
                jsonPath("$.proposals[0].participants", hasSize<Any>(2))
                jsonPath("$.proposals[0].citations", hasSize<Any>(2))
                jsonPath("$.proposals[0].citations[1].chunkId") { value("video:IdpXx2gm90o") }
            }
        mockMvc
            .get("/api/v1/coach/runs/$runId") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect {
                status { isOk() }
                jsonPath("$.canApprove") { value(false) }
            }

        // 자녀 승인 → 403, 부모 승인 → 미션 생성, 재승인 → 409
        mockMvc
            .post("/api/v1/coach/runs/$runId/approve") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("NOT_A_PARENT") }
            }
        val missionId =
            mockMvc
                .post("/api/v1/coach/runs/$runId/approve") { header(HttpHeaders.AUTHORIZATION, parent) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("APPROVED") }
                    jsonPath("$.approvedBy") { value(parentId.toString()) }
                    jsonPath("$.createdMissions", hasSize<Any>(1))
                    jsonPath("$.createdMissions[0].origin") { value("COACH") }
                }.andReturn()
                .let { Regex("\"missionId\":\"([^\"]+)\"").find(it.response.contentAsString)!!.groupValues[1] }
        mockMvc
            .post("/api/v1/coach/runs/$runId/approve") { header(HttpHeaders.AUTHORIZATION, parent) }
            .andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("ALREADY_APPROVED") }
            }

        // 미션 목록
        mockMvc
            .get("/api/v1/families/$familyId/missions") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect {
                status { isOk() }
                jsonPath("$.missions", hasSize<Any>(1))
                jsonPath("$.missions[0].missionId") { value(missionId) }
                jsonPath("$.missions[0].origin") { value("COACH") }
                jsonPath("$.missions[0].coachRunId") { value(runId) }
                jsonPath("$.missions[0].serverVerifiable") { value(true) }
                jsonPath("$.missions[0].video.durationSec") { value(600) }
                jsonPath("$.missions[0].participants", hasSize<Any>(2))
                jsonPath("$.missions[0].participants[?(@.profileId=='$childId')].name") { value("민준") }
                jsonPath("$.missions[0].participants[?(@.profileId=='$childId')].progress") { value(0.0) }
            }

        // 타이머: 경과 20분으로 자르고, 활동 합계(모의) 45분 → 완료
        childTotals = ActivityTotals(0, 45, 45)
        val startedAt =
            time
                .today()
                .atTime(8, 30)
                .atZone(time.zone)
                .toInstant()
        mockMvc
            .post("/api/v1/missions/$missionId/activity/timer") {
                header(HttpHeaders.AUTHORIZATION, child)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"profileId":"$childId","startedAt":"$startedAt","endedAt":"${startedAt.plusSeconds(20 * 60)}","activeMinutes":60}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.activityDate") { value(time.today().toString()) }
                jsonPath("$.source") { value("TIMER") }
                jsonPath("$.serverVerified") { value(true) }
                jsonPath("$.totalActiveMinutes") { value(20) }
                jsonPath("$.missionProgress") { value(1.0) }
                jsonPath("$.missionCompleted") { value(true) }
            }
        org.mockito.Mockito
            .verify(activityRecorder)
            .addActiveMinutes(childId, time.today(), ActivitySource.TIMER, 20)

        // 영상 진행률: 최초 완주에만 10분 적립
        mockMvc
            .post("/api/v1/videos/IdpXx2gm90o/progress") {
                header(HttpHeaders.AUTHORIZATION, child)
                contentType = MediaType.APPLICATION_JSON
                content = """{"profileId":"$childId","progress":0.95,"watchedSec":570}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.maxProgress") { value(0.95) }
                jsonPath("$.completed") { value(true) }
                jsonPath("$.creditedMinutes") { value(10) }
                jsonPath("$.verifiedBy") { value("VIDEO_PROGRESS") }
            }
        mockMvc
            .post("/api/v1/videos/IdpXx2gm90o/progress") {
                header(HttpHeaders.AUTHORIZATION, child)
                contentType = MediaType.APPLICATION_JSON
                content = """{"profileId":"$childId","progress":1.0,"watchedSec":600,"missionId":"$missionId"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.creditedMinutes") { value(0) }
                jsonPath("$.missionProgress") { value(1.0) }
            }
        mockMvc
            .post("/api/v1/videos/nope/progress") {
                header(HttpHeaders.AUTHORIZATION, child)
                contentType = MediaType.APPLICATION_JSON
                content = """{"profileId":"$childId","progress":0.5,"watchedSec":10}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("VIDEO_NOT_FOUND") }
            }

        // 대화(스텁): 인용 있는 답, 의료 질문은 거부(200)
        val conversationId =
            mockMvc
                .post("/api/v1/coach/chat") {
                    header(HttpHeaders.AUTHORIZATION, child)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"profileId":"$childId","question":"유연성에 좋은 준비운동이 뭐예요?"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.refused") { value(false) }
                    jsonPath("$.citations", hasSize<Any>(1))
                    jsonPath("$.citations[0].sourceLabel") { value("국민체력100 운동처방 · 유소년 11세") }
                    jsonPath("$.citations[0].excerpt") { value("국민체력100 운동처방 · 유소년 11세") }
                }.andReturn()
                .let { Regex("\"conversationId\":\"([^\"]+)\"").find(it.response.contentAsString)!!.groupValues[1] }
        mockMvc
            .post("/api/v1/coach/chat") {
                header(HttpHeaders.AUTHORIZATION, parent)
                contentType = MediaType.APPLICATION_JSON
                content = """{"profileId":"$childId","conversationId":"$conversationId","question":"무릎 통증이 있어요"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.conversationId") { value(conversationId) }
                jsonPath("$.refused") { value(true) }
                jsonPath("$.refusalReason") { value("medical_query") }
            }
        mockMvc
            .post("/api/v1/coach/chat") {
                header(HttpHeaders.AUTHORIZATION, parent)
                contentType = MediaType.APPLICATION_JSON
                content = """{"profileId":"$parentId","conversationId":"$conversationId","question":"내 대화가 아닌데요"}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("FORBIDDEN") }
            }

        // 주간 요약
        mockMvc
            .get("/api/v1/families/$familyId/report/weekly") { header(HttpHeaders.AUTHORIZATION, parent) }
            .andExpect {
                status { isOk() }
                jsonPath("$.weekStart") { value(time.thisWeekStart().toString()) }
                jsonPath("$.weekEnd") { value(time.thisWeekStart().plusDays(6).toString()) }
                jsonPath("$.summary") { value("유연성은 매일 조금씩 늘려 가는 영역입니다. 한 주 3회, 회당 15분이면 충분합니다.") }
                jsonPath("$.missionStats.total") { value(1) }
                jsonPath("$.missionStats.completed") { value(0) }
                jsonPath("$.members", hasSize<Any>(3))
                jsonPath("$.members[?(@.profileId=='$childId')].verifiedMinutes") { value(45) }
                jsonPath("$.members[?(@.profileId=='$childId')].completedMissions") { value(1) }
                jsonPath("$.cheerCount") { value(2) }
            }

        // 인증 없음 → 401, 다른 가족 → 403
        mockMvc.get("/api/v1/coach/runs/$runId").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("UNAUTHORIZED") }
        }
        mockMvc
            .get("/api/v1/families/$familyId/missions") { header(HttpHeaders.AUTHORIZATION, auth.bearer(family.outsiderUser)) }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("NOT_SAME_FAMILY") }
            }
    }

    @Test
    fun `직접 만든 걸음수 미션은 보호자 확인으로 끝나고 영상 목록은 필터·즐겨찾기를 지원한다`() {
        val parent = auth.bearer(family.parentUser)
        val child = auth.bearer(family.childUser)
        val today = time.today()

        mockMvc
            .post("/api/v1/families/$familyId/missions") {
                header(HttpHeaders.AUTHORIZATION, child)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"title":"걷기","startDate":"$today","endDate":"$today","targetMetric":"STEPS","targetValue":3000,"participantProfileIds":["$childId"]}"""
            }.andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/families/$familyId/missions") {
                header(HttpHeaders.AUTHORIZATION, parent)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"title":"","startDate":"$today","endDate":"$today","targetMetric":"STEPS","targetValue":3000,"participantProfileIds":["$childId"]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("BAD_REQUEST") }
            }
        mockMvc
            .post("/api/v1/families/$familyId/missions") {
                header(HttpHeaders.AUTHORIZATION, parent)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"title":"걷기","startDate":"$today","endDate":"$today","targetMetric":"STEPS","targetValue":3000,"participantProfileIds":["${UUID.randomUUID()}"]}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.error.code") { value("NOT_FAMILY_MEMBER") }
            }
        val missionId =
            mockMvc
                .post("/api/v1/families/$familyId/missions") {
                    header(HttpHeaders.AUTHORIZATION, parent)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"title":"걷기","startDate":"$today","endDate":"${today.plusDays(
                            2,
                        )}","targetMetric":"STEPS","targetValue":3000,"participantProfileIds":["$childId"]}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.origin") { value("MANUAL") }
                    jsonPath("$.coachRunId") { value(null) }
                    jsonPath("$.serverVerifiable") { value(false) }
                }.andReturn()
                .let { Regex("\"missionId\":\"([^\"]+)\"").find(it.response.contentAsString)!!.groupValues[1] }

        mockMvc
            .post("/api/v1/missions/$missionId/participants/$childId/confirm") { header(HttpHeaders.AUTHORIZATION, parent) }
            .andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.error.code") { value("TARGET_NOT_REACHED") }
            }

        childTotals = ActivityTotals(3500, 0, 0)
        mockMvc
            .post("/api/v1/missions/$missionId/activity/steps") {
                header(HttpHeaders.AUTHORIZATION, child)
                contentType = MediaType.APPLICATION_JSON
                content = """{"profileId":"$childId","activityDate":"$today","steps":3500}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.source") { value("MANUAL") }
                jsonPath("$.serverVerified") { value(false) }
                jsonPath("$.verifiedBy") { value("SELF_REPORT") }
                jsonPath("$.missionProgress") { value(1.0) }
                jsonPath("$.missionCompleted") { value(false) }
                jsonPath("$.needsGuardianCheck") { value(true) }
            }
        mockMvc
            .post("/api/v1/missions/$missionId/activity/timer") {
                header(HttpHeaders.AUTHORIZATION, child)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"profileId":"$childId","startedAt":"${Instant.now().minusSeconds(
                        600,
                    )}","endedAt":"${Instant.now()}","activeMinutes":5}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.error.code") { value("INVALID_METRIC") }
            }
        mockMvc
            .post("/api/v1/missions/$missionId/participants/$childId/confirm") { header(HttpHeaders.AUTHORIZATION, parent) }
            .andExpect {
                status { isOk() }
                jsonPath("$.completed") { value(true) }
                jsonPath("$.verifiedBy") { value("SELF_REPORT") }
                jsonPath("$.confirmedBy") { value(parentId.toString()) }
            }
        mockMvc
            .get("/api/v1/families/$familyId/missions?scope=MINE&status=DONE") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect {
                status { isOk() }
                jsonPath("$.missions", hasSize<Any>(1))
                jsonPath("$.missions[0].participants[0].completed") { value(true) }
                jsonPath("$.missions[0].participants[0].needsGuardianCheck") { value(false) }
            }

        // 영상 목록: 유소년 안전 필터 + 요인, 커서, 즐겨찾기
        mockMvc
            .get("/api/v1/videos?ageGroup=유소년&factor=유연성&size=1") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect {
                status { isOk() }
                jsonPath("$.videos", hasSize<Any>(1))
                jsonPath("$.videos[0].videoId") { value("IdpXx2gm90o") }
                jsonPath("$.videos[0].thumbnailUrl") { value("https://i.ytimg.com/vi/IdpXx2gm90o/hqdefault.jpg") }
                jsonPath("$.videos[0].label.factors[0]") { value("유연성") }
                jsonPath("$.videos[0].favorited") { value(false) }
                jsonPath("$.nextCursor") { value("IdpXx2gm90o") }
            }
        mockMvc
            .get("/api/v1/videos?ageGroup=유소년&factor=유연성&size=1&cursor=IdpXx2gm90o") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect {
                status { isOk() }
                jsonPath("$.videos[0].videoId") { value("sample00002") }
                jsonPath("$.nextCursor") { value(null) }
            }
        mockMvc
            .get("/api/v1/videos?list=FAVORITES") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect { status { isBadRequest() } }
        mockMvc
            .post("/api/v1/videos/sample00003/favorite") {
                header(HttpHeaders.AUTHORIZATION, parent)
                contentType = MediaType.APPLICATION_JSON
                content = """{"profileId":"$childId","favorited":true}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.favorited") { value(true) }
            }
        mockMvc
            .get("/api/v1/videos?list=FAVORITES&profileId=$childId") { header(HttpHeaders.AUTHORIZATION, child) }
            .andExpect {
                status { isOk() }
                jsonPath("$.videos", hasSize<Any>(1))
                jsonPath("$.videos[0].videoId") { value("sample00003") }
                jsonPath("$.videos[0].favorited") { value(true) }
                jsonPath("$.videos[0].maxProgress") { value(closeTo(0.0, 0.0001)) }
            }
    }

    private fun insertFamilyRows() {
        val now = Instant.now()
        jdbc
            .sql("insert into families (id, name, created_at, updated_at) values (?, ?, ?, ?)")
            .params(familyId, "테스트가족", now, now)
            .update()
        family.members.forEach { m ->
            jdbc
                .sql(
                    "insert into users (id, provider, provider_user_id, status, created_at, updated_at) values (?, 'GOOGLE', ?, 'ACTIVE', ?, ?)",
                ).params(listOf(m.userId, "sub-${m.userId}", now, now))
                .update()
            jdbc
                .sql(
                    """
                    insert into profiles (id, family_id, user_id, display_name, birth_date, sex, role, is_owner, support_mode, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).params(
                    listOf(
                        m.profileId,
                        familyId,
                        m.userId,
                        m.name,
                        m.birthDate,
                        m.sex.name,
                        m.role.name,
                        m === family.parent,
                        m.supportMode?.name,
                        now,
                        now,
                    ),
                ).update()
        }
    }
}
