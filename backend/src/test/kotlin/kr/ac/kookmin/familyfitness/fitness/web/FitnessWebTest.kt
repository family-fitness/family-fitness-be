package kr.ac.kookmin.familyfitness.fitness.web

import kr.ac.kookmin.familyfitness.fitness.application.detailsOf
import kr.ac.kookmin.familyfitness.fitness.application.summaryOf
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryResponse
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Copy
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.support.ProfileRows
import kr.ac.kookmin.familyfitness.support.TestAuth
import kr.ac.kookmin.familyfitness.support.anyArg
import kr.ac.kookmin.familyfitness.support.capture
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * H2 + Flyway(V1~V3, 국민체력100 규준 2024-07~2026-07) 위에서 fitness 웹 어댑터를 끝까지 돈다.
 * identity·AI 는 목: 같은 가족 판단과 프로필 상세, 예측 응답을 흉내 낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FitnessWebTest {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var auth: TestAuth

    @Autowired lateinit var rows: ProfileRows

    @MockitoBean lateinit var familyAccess: FamilyAccess

    @MockitoBean lateinit var profileQuery: ProfileQuery

    @MockitoBean lateinit var cheerQuery: CheerQuery

    @MockitoBean lateinit var ai: AiGateway

    private val userId = UUID.randomUUID()
    private lateinit var familyId: UUID
    private lateinit var childId: UUID

    private val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val testedOn: LocalDate = today.minusDays(1)

    /** 항상 만 9세(유소년)가 되도록 생년월일을 오늘 기준으로 잡는다. */
    private val birthDate: LocalDate = today.minusYears(11).minusMonths(4)

    @BeforeEach
    fun setUp() {
        familyId = rows.family()
        childId = rows.profile(familyId, birthDate, Sex.F)
        `when`(familyAccess.requireSameFamilyAsProfile(userId, childId))
            .thenReturn(summaryOf(childId, familyId, AgeGroup.YOUTH))
        `when`(profileQuery.findDetails(childId)).thenReturn(detailsOf(childId, familyId, birthDate, Sex.F))
    }

    private fun bearer() = auth.bearer(userId)

    private fun registerBody(vararg items: Pair<String, String>): String =
        """
        {"testedOn":"$testedOn","source":"SELF_INPUT","heightCm":135.5,"weightKg":31.2,
         "items":[${items.joinToString(",") { (code, v) -> """{"itemCode":"$code","value":$v}""" }}]}
        """.trimIndent()

    private fun registerYouthTest() =
        mvc.perform(
            post("/api/v1/profiles/$childId/fitness-tests")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("012" to "9", "020" to "42", "022" to "142", "028" to "30")),
        )

    @Test
    fun `토큰 없이 부르면 401`() {
        mvc
            .perform(get("/api/v1/fitness/items").param("ageGroup", "유소년"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        mvc
            .perform(get("/api/v1/profiles/$childId/fitness-tests/latest"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `유소년 측정 항목 목록`() {
        mvc
            .perform(get("/api/v1/fitness/items").param("ageGroup", "유소년").param("sex", "F").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ageGroup").value("유소년"))
            .andExpect(jsonPath("$.items", hasSize<Any>(6)))
            .andExpect(jsonPath("$.items[*].itemCode", containsInAnyOrder("009", "012", "028", "020", "022", "043")))
            .andExpect(jsonPath("$.items[?(@.itemCode=='020')].itemLabel", contains("15m 왕복오래달리기")))
            .andExpect(jsonPath("$.items[?(@.itemCode=='020')].factor", contains("심폐지구력")))
            .andExpect(jsonPath("$.items[?(@.itemCode=='020')].inputGroup", contains("EQUIPMENT")))
            .andExpect(jsonPath("$.items[?(@.itemCode=='020')].optional", contains(true)))
            .andExpect(jsonPath("$.items[?(@.itemCode=='028')].equipment", contains("악력계")))
            .andExpect(jsonPath("$.items[?(@.itemCode=='012')].optional", contains(false)))
            .andExpect(jsonPath("$.items[?(@.itemCode=='012')].equipment", everyItem(nullValue())))
            .andExpect(jsonPath("$.items[?(@.itemCode=='012')].range.min", contains(-30)))
            .andExpect(jsonPath("$.items[?(@.itemCode=='012')].higherIsBetter", contains(true)))
    }

    @Test
    fun `모르는 연령대는 400`() {
        mvc
            .perform(get("/api/v1/fitness/items").param("ageGroup", "노년").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `유소년 여아 11세 측정을 등록하면 국민체력100 규준으로 백분위가 붙는다`() {
        registerYouthTest()
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.fitnessTestId").isNotEmpty)
            .andExpect(jsonPath("$.testedOn").value(testedOn.toString()))
            .andExpect(jsonPath("$.items", hasSize<Any>(4)))
            .andExpect(jsonPath("$.items[?(@.itemCode=='012')].percentile", contains(48)))
            .andExpect(jsonPath("$.items[?(@.itemCode=='012')].grade", contains("참가")))
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
            .andExpect(jsonPath("$.disclaimer").value(Copy.FITNESS_DISCLAIMER))
    }

    @Test
    fun `등록 오류 코드 — 혈압·항목 없음·중복 날짜·연령대 밖 항목·동의`() {
        mvc
            .perform(
                post("/api/v1/profiles/$childId/fitness-tests")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("028" to "30", "005" to "80")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("ITEM_NOT_ALLOWED"))

        mvc
            .perform(
                post("/api/v1/profiles/$childId/fitness-tests")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody()),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("NO_ITEMS"))

        mvc
            .perform(
                post("/api/v1/profiles/$childId/fitness-tests")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("013" to "20")),
            ).andExpect(status().`is`(422))
            .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOR_AGE_GROUP"))

        registerYouthTest().andExpect(status().isCreated)
        registerYouthTest()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_DATE"))

        `when`(familyAccess.requireSameFamilyAsProfile(userId, childId))
            .thenReturn(summaryOf(childId, familyId, AgeGroup.YOUTH, measurable = false, consentGiven = false))
        mvc
            .perform(
                post("/api/v1/profiles/$childId/fitness-tests")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("028" to "30")),
            ).andExpect(status().`is`(422))
            .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"))
    }

    @Test
    fun `요청 형식이 틀리면 400 BAD_REQUEST`() {
        mvc
            .perform(
                post("/api/v1/profiles/$childId/fitness-tests")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"testedOn":"$testedOn","source":"SELF_INPUT","heightCm":10,"items":[{"itemCode":"028","value":30}]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `다른 가족의 프로필이면 403`() {
        val stranger = UUID.randomUUID()
        `when`(familyAccess.requireSameFamilyAsProfile(stranger, childId)).thenThrow(NotSameFamilyException())
        mvc
            .perform(get("/api/v1/profiles/$childId/fitness-tests/latest").header(HttpHeaders.AUTHORIZATION, auth.bearer(stranger)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"))
    }

    @Test
    fun `이력이 없으면 latest 는 200 에 null 과 빈 레이더`() {
        mvc
            .perform(get("/api/v1/profiles/$childId/fitness-tests/latest").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fitnessTestId").value(nullValue()))
            .andExpect(jsonPath("$.testedOn").value(nullValue()))
            .andExpect(jsonPath("$.radar", hasSize<Any>(5)))
            .andExpect(jsonPath("$.radar[0].factor").value("근력"))
            .andExpect(jsonPath("$.radar[0].percentile").value(nullValue()))
            .andExpect(jsonPath("$.radar[4].factor").value("순발력"))
            .andExpect(jsonPath("$.items", hasSize<Any>(0)))
            .andExpect(jsonPath("$.weakest").value(nullValue()))
            .andExpect(jsonPath("$.strongest").value(nullValue()))
            .andExpect(jsonPath("$.coachDirection").value("GROWTH"))
            .andExpect(jsonPath("$.disclaimer").value(Copy.FITNESS_DISCLAIMER))
    }

    @Test
    fun `latest 는 레이더 5요인과 코치 방향을 준다`() {
        registerYouthTest().andExpect(status().isCreated)

        mvc
            .perform(get("/api/v1/profiles/$childId/fitness-tests/latest").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fitnessTestId").isNotEmpty)
            .andExpect(jsonPath("$.testedOn").value(testedOn.toString()))
            .andExpect(jsonPath("$.radar[*].factor", contains("근력", "근지구력", "유연성", "심폐지구력", "순발력")))
            .andExpect(jsonPath("$.radar[0].percentile").value(10))
            .andExpect(jsonPath("$.radar[1].percentile").value(nullValue()))
            .andExpect(jsonPath("$.radar[2].percentile").value(48))
            .andExpect(jsonPath("$.radar[3].percentile").value(35))
            .andExpect(jsonPath("$.radar[4].percentile").value(63))
            .andExpect(jsonPath("$.items", hasSize<Any>(4)))
            .andExpect(jsonPath("$.items[?(@.itemCode=='028')].grade", contains("참가")))
            .andExpect(jsonPath("$.weakest.itemCode").value("028"))
            .andExpect(jsonPath("$.strongest.itemCode").value("022"))
            .andExpect(jsonPath("$.coachDirection").value("GROWTH"))
    }

    @Test
    fun `예측은 AI 응답을 MAINTAIN 포인트로 저장해 201 로 돌려준다`() {
        registerYouthTest().andExpect(status().isCreated)
        `when`(ai.trajectory(anyArg())).thenReturn(
            TrajectoryResponse(
                basis = "cross_sectional_group_distribution",
                itemCode = "028",
                itemName = "상대악력",
                unit = "%",
                bands =
                    listOf(
                        TrajectoryResponse.Band(11, 28.0, 36.0, 44.0, 120),
                        TrajectoryResponse.Band(14, 32.0, 40.0, 50.0, 110),
                        TrajectoryResponse.Band(21, 45.0, 60.0, 75.0, 90),
                    ),
                notice = Copy.TRAJECTORY_NOTICE,
                lowSample = false,
            ),
        )

        mvc
            .perform(
                post("/api/v1/profiles/$childId/predictions")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"horizonYears":10,"itemCode":"028"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.predictionId").isNotEmpty)
            .andExpect(jsonPath("$.modelVersion").value("ai-trajectory-v1"))
            .andExpect(jsonPath("$.basis").value("cross_sectional_group_distribution"))
            .andExpect(jsonPath("$.notice").value(Copy.TRAJECTORY_NOTICE))
            .andExpect(jsonPath("$.points", hasSize<Any>(3)))
            .andExpect(jsonPath("$.points[*].scenario", contains("MAINTAIN", "MAINTAIN", "MAINTAIN")))
            .andExpect(jsonPath("$.points[*].yearsFromNow", contains(0, 3, 10)))
            .andExpect(jsonPath("$.points[1].itemCode").value("028"))
            .andExpect(jsonPath("$.points[1].p10").value(32.0))
            .andExpect(jsonPath("$.points[1].p50").value(40.0))
            .andExpect(jsonPath("$.points[1].p90").value(50.0))
    }

    @Test
    fun `예측 오류 — 측정 없음 422, AI 장애 503`() {
        mvc
            .perform(post("/api/v1/profiles/$childId/predictions").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().`is`(422))
            .andExpect(jsonPath("$.error.code").value("NO_FITNESS_TEST"))

        registerYouthTest().andExpect(status().isCreated)
        `when`(ai.trajectory(anyArg())).thenThrow(AiUnavailableException("AI 서비스 응답 없음"))
        mvc
            .perform(post("/api/v1/profiles/$childId/predictions").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.error.code").value("TEMPORARILY_UNAVAILABLE"))
    }
}
