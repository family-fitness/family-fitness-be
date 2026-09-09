package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web

import kr.ac.kookmin.familyfitness.support.TestAuth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID

/**
 * H2(PostgreSQL 모드) + Flyway + 실제 보안 필터를 거치는 identity 엔드포인트 통합 테스트.
 * 계정은 개발용 로그인(`app.auth.dev-login.enabled=true`)으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityApiTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var testAuth: TestAuth

    private val today: LocalDate = LocalDate.now()

    private data class Session(
        val userId: UUID,
        val bearer: String,
        val refreshToken: String,
        val body: JsonNode,
    )

    private fun devLogin(
        providerUserId: String = "dev-" + UUID.randomUUID(),
        claimCode: String? = null,
    ): Session {
        val request = mutableMapOf<String, Any?>("providerUserId" to providerUserId, "email" to "$providerUserId@example.com")
        if (claimCode != null) request["claimCode"] = claimCode
        val body =
            mvc
                .perform(post("/api/v1/auth/dev-login").json(request))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isString)
                .andExpect(jsonPath("$.refreshToken").isString)
                .andReturn()
                .response.contentAsString
                .let(json::readTree)
        return Session(
            userId = UUID.fromString(body["userId"].asString()),
            bearer = "Bearer " + body["accessToken"].asString(),
            refreshToken = body["refreshToken"].asString(),
            body = body,
        )
    }

    private fun MockHttpServletRequestBuilder.json(body: Any): MockHttpServletRequestBuilder =
        contentType(MediaType.APPLICATION_JSON).content(if (body is String) body else json.writeValueAsString(body))

    private fun MockHttpServletRequestBuilder.auth(session: Session): MockHttpServletRequestBuilder =
        header(HttpHeaders.AUTHORIZATION, session.bearer)

    private fun createFamily(
        session: Session,
        familyName: String = "우리 가족",
    ): JsonNode =
        mvc
            .perform(
                post("/api/v1/families")
                    .auth(session)
                    .json(mapOf("familyName" to familyName, "owner" to mapOf("name" to "엄마", "birthDate" to "1988-03-01", "sex" to "F"))),
            ).andExpect(status().isCreated)
            .andReturn()
            .response.contentAsString
            .let(json::readTree)

    private fun addMember(
        session: Session,
        familyId: String,
        name: String,
        birthDate: LocalDate,
        role: String,
        consent: Pair<Boolean, Boolean>? = null,
        sex: String = "M",
    ) = mvc.perform(
        post("/api/v1/families/$familyId/profiles")
            .auth(session)
            .json(
                buildMap {
                    put("name", name)
                    put("birthDate", birthDate.toString())
                    put("sex", sex)
                    put("role", role)
                    if (consent != null) put("guardianConsent", mapOf("personalData" to consent.first, "healthData" to consent.second))
                },
            ),
    )

    private fun invite(
        session: Session,
        profileId: String,
    ) = mvc.perform(post("/api/v1/profiles/$profileId/invite").auth(session))

    private fun claim(
        session: Session,
        code: String,
    ) = mvc.perform(post("/api/v1/profiles/claim").auth(session).json(mapOf("claimCode" to code)))

    private fun read(action: org.springframework.test.web.servlet.ResultActions): JsonNode =
        json.readTree(action.andReturn().response.contentAsString)

    @Test
    fun `개발용 로그인 → 가족 생성 → 아이 추가 → 목록`() {
        val parent = devLogin()
        assertThat(parent.body["nextStep"].asString()).isEqualTo("CREATE_FAMILY")
        assertThat(parent.body["profiles"].isEmpty).isTrue()

        val family = createFamily(parent)
        val familyId = family["familyId"].asString()
        assertThat(family["familyName"].asString()).isEqualTo("우리 가족")
        val owner = family["ownerProfile"]
        assertThat(owner["role"].asString()).isEqualTo("PARENT")
        assertThat(owner["hasAccount"].asBoolean()).isTrue()
        assertThat(owner["ageGroup"].asString()).isEqualTo("성인")
        assertThat(owner["inviteStatus"].asString()).isEqualTo("NONE")
        assertThat(owner["supportMode"].isNull).isTrue()
        assertThat(owner["measurable"].asBoolean()).isTrue()
        assertThat(owner["consentRequired"].asBoolean()).isFalse()
        assertThat(owner["consentGiven"].asBoolean()).isTrue()
        assertThat(owner["familyId"].asString()).isEqualTo(familyId)

        mvc
            .perform(
                post("/api/v1/families")
                    .auth(parent)
                    .json(mapOf("familyName" to "또", "owner" to mapOf("name" to "엄마", "birthDate" to "1988-03-01", "sex" to "F"))),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ALREADY_IN_FAMILY"))

        val childBirth = today.minusYears(8)
        addMember(parent, familyId, "첫째", childBirth, "CHILD")
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"))
        addMember(parent, familyId, "첫째", childBirth, "CHILD", consent = true to false)
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"))

        val child =
            read(
                addMember(parent, familyId, "첫째", childBirth, "CHILD", consent = true to true)
                    .andExpect(status().isCreated),
            )
        assertThat(child["profileId"].asString()).isNotBlank()
        assertThat(child["name"].asString()).isEqualTo("첫째")
        assertThat(child["role"].asString()).isEqualTo("CHILD")
        assertThat(child["ageGroup"].asString()).isEqualTo("유소년")
        assertThat(child["hasAccount"].asBoolean()).isFalse()
        assertThat(child["measurable"].asBoolean()).isTrue()
        assertThat(child["consentRequired"].asBoolean()).isTrue()
        assertThat(child["consentGiven"].asBoolean()).isTrue()

        val toddler =
            read(
                addMember(parent, familyId, "막내", today.minusYears(2), "CHILD", consent = true to true)
                    .andExpect(status().isCreated),
            )
        assertThat(toddler["ageGroup"].asString()).isEqualTo("유아기")
        assertThat(toddler["measurable"].asBoolean()).isFalse()

        val listed =
            read(
                mvc
                    .perform(get("/api/v1/families/$familyId/profiles").auth(parent))
                    .andExpect(status().isOk),
            )
        assertThat(listed["familyId"].asString()).isEqualTo(familyId)
        assertThat(listed["familyName"].asString()).isEqualTo("우리 가족")
        assertThat(listed["profiles"].size()).isEqualTo(3)

        val stranger = devLogin()
        mvc
            .perform(get("/api/v1/families/$familyId/profiles").auth(stranger))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"))
        addMember(stranger, familyId, "침입", childBirth, "CHILD", consent = true to true)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"))
        mvc
            .perform(get("/api/v1/families/${UUID.randomUUID()}/profiles").auth(parent))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FAMILY_NOT_FOUND"))

        val me = read(mvc.perform(get("/api/v1/me").auth(parent)).andExpect(status().isOk))
        assertThat(me["userId"].asString()).isEqualTo(parent.userId.toString())
        assertThat(me["nextStep"].asString()).isEqualTo("HOME")
        assertThat(me["profiles"].size()).isEqualTo(1)
    }

    @Test
    fun `초대 → 두 번째 계정이 코드 사용 → 참여 수준 → 동의 철회`() {
        val parent = devLogin()
        val familyId = createFamily(parent)["familyId"].asString()
        val ownerId = read(mvc.perform(get("/api/v1/me").auth(parent)))["profiles"][0]["profileId"].asString()
        val childId = read(addMember(parent, familyId, "첫째", today.minusYears(10), "CHILD", consent = true to true))["profileId"].asString()
        val dadId = read(addMember(parent, familyId, "아빠", today.minusYears(40), "PARENT"))["profileId"].asString()

        invite(parent, ownerId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"))
        invite(devLogin(), childId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"))

        val invitation = read(invite(parent, childId).andExpect(status().isCreated))
        val code = invitation["claimCode"].asString()
        assertThat(code).matches("[A-HJ-NP-Z2-9]{6}")
        assertThat(invitation["expiresAt"].asString()).isNotBlank()
        assertThat(invitation["shareUrl"].asString()).isEqualTo("http://localhost:5173/claim?code=$code")
        val issued = read(mvc.perform(get("/api/v1/families/$familyId/profiles").auth(parent)))["profiles"]
        assertThat(issued.first { it["profileId"].asString() == childId }["inviteStatus"].asString()).isEqualTo("ISSUED")

        val childUser = devLogin(claimCode = code)
        assertThat(childUser.body["nextStep"].asString()).isEqualTo("CLAIM")

        claim(childUser, "zzzzzz")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("CODE_NOT_FOUND"))
        claim(parent, code)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"))

        val claimed = read(claim(childUser, code.lowercase()).andExpect(status().isOk))
        assertThat(claimed["profileId"].asString()).isEqualTo(childId)
        assertThat(claimed["familyId"].asString()).isEqualTo(familyId)
        assertThat(claimed["role"].asString()).isEqualTo("CHILD")
        assertThat(claimed["nextStep"].asString()).isEqualTo("HOME")

        claim(devLogin(), code)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"))
        invite(parent, childId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"))

        val childMe = read(mvc.perform(get("/api/v1/me").auth(childUser)).andExpect(status().isOk))
        assertThat(childMe["nextStep"].asString()).isEqualTo("HOME")
        assertThat(childMe["profiles"][0]["profileId"].asString()).isEqualTo(childId)
        assertThat(childMe["profiles"][0]["hasAccount"].asBoolean()).isTrue()
        assertThat(childMe["profiles"][0]["inviteStatus"].asString()).isEqualTo("CLAIMED")

        val dadCode = read(invite(parent, dadId).andExpect(status().isCreated))["claimCode"].asString()
        val dadUser = devLogin()
        assertThat(read(claim(dadUser, dadCode).andExpect(status().isOk))["nextStep"].asString()).isEqualTo("SUPPORT_MODE")

        // 참여 수준: 본인 PARENT 프로필만
        val changed =
            read(
                mvc
                    .perform(patch("/api/v1/profiles/$dadId/support-mode").auth(dadUser).json(mapOf("supportMode" to "WEEKEND")))
                    .andExpect(status().isOk),
            )
        assertThat(changed["supportMode"].asString()).isEqualTo("WEEKEND")
        assertThat(changed["profileId"].asString()).isEqualTo(dadId)
        mvc
            .perform(patch("/api/v1/profiles/$childId/support-mode").auth(childUser).json(mapOf("supportMode" to "FULL")))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("NOT_APPLICABLE"))
        mvc
            .perform(patch("/api/v1/profiles/$dadId/support-mode").auth(parent).json(mapOf("supportMode" to "FULL")))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        mvc
            .perform(patch("/api/v1/profiles/$ownerId/support-mode").auth(parent).json(mapOf("supportMode" to "NOPE")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))

        // 동의 철회·재동의: 가족의 PARENT 만
        mvc
            .perform(patch("/api/v1/profiles/$childId/consent").auth(childUser).json(mapOf("personalData" to true, "healthData" to true)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("NOT_A_PARENT"))
        val revoked =
            read(
                mvc
                    .perform(
                        patch("/api/v1/profiles/$childId/consent").auth(parent).json(
                            mapOf(
                                "personalData" to true,
                                "healthData" to false,
                            ),
                        ),
                    ).andExpect(status().isOk),
            )
        assertThat(revoked["consentGiven"].asBoolean()).isFalse()
        assertThat(revoked["consentAt"].isNull).isTrue()
        assertThat(revoked["consentBy"].isNull).isTrue()
        assertThat(revoked["measurable"].asBoolean()).isFalse()
        val afterRevoke = read(mvc.perform(get("/api/v1/me").auth(childUser)))["profiles"][0]
        assertThat(afterRevoke["consentGiven"].asBoolean()).isFalse()
        assertThat(afterRevoke["measurable"].asBoolean()).isFalse()

        val regranted =
            read(
                mvc
                    .perform(
                        patch("/api/v1/profiles/$childId/consent").auth(dadUser).json(
                            mapOf(
                                "personalData" to true,
                                "healthData" to true,
                            ),
                        ),
                    ).andExpect(status().isOk),
            )
        assertThat(regranted["consentGiven"].asBoolean()).isTrue()
        assertThat(regranted["consentAt"].asString()).isNotBlank()
        assertThat(regranted["consentBy"].asString()).isEqualTo(dadUser.userId.toString())
        assertThat(regranted["measurable"].asBoolean()).isTrue()
        mvc
            .perform(patch("/api/v1/profiles/$childId/consent").auth(parent).json(mapOf("personalData" to true)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `응원 규칙과 과다 호출`() {
        val parent = devLogin()
        val familyId = createFamily(parent)["familyId"].asString()
        val ownerId = read(mvc.perform(get("/api/v1/me").auth(parent)))["profiles"][0]["profileId"].asString()
        val childId = read(addMember(parent, familyId, "첫째", today.minusYears(10), "CHILD", consent = true to true))["profileId"].asString()

        fun cheer(
            session: Session,
            body: Map<String, Any?>,
        ) = mvc.perform(post("/api/v1/families/$familyId/cheers").auth(session).json(body))

        val created =
            read(
                cheer(parent, mapOf("fromProfileId" to ownerId, "toProfileId" to childId, "message" to "힘내!", "emoji" to "💪"))
                    .andExpect(status().isCreated),
            )
        assertThat(created["cheerId"].asString()).isNotBlank()
        assertThat(created["fromProfileId"].asString()).isEqualTo(ownerId)
        assertThat(created["toProfileId"].asString()).isEqualTo(childId)
        assertThat(created["message"].asString()).isEqualTo("힘내!")
        assertThat(created["emoji"].asString()).isEqualTo("💪")
        assertThat(created["missionId"].isNull).isTrue()
        assertThat(created["createdAt"].asString()).isNotBlank()

        cheer(parent, mapOf("fromProfileId" to ownerId, "toProfileId" to ownerId, "message" to "나"))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("SELF_CHEER"))
        cheer(parent, mapOf("fromProfileId" to ownerId, "toProfileId" to UUID.randomUUID().toString(), "message" to "?"))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("NOT_FAMILY_MEMBER"))
        cheer(parent, mapOf("fromProfileId" to childId, "toProfileId" to ownerId, "message" to "?"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        cheer(devLogin(), mapOf("fromProfileId" to ownerId, "toProfileId" to childId, "message" to "?"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"))
        cheer(parent, mapOf("fromProfileId" to ownerId, "toProfileId" to childId))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
        cheer(parent, mapOf("fromProfileId" to ownerId, "toProfileId" to childId, "message" to "가".repeat(101)))
            .andExpect(status().isBadRequest)

        repeat(4) {
            cheer(parent, mapOf("fromProfileId" to ownerId, "toProfileId" to childId, "emoji" to "👍")).andExpect(status().isCreated)
        }
        cheer(parent, mapOf("fromProfileId" to ownerId, "toProfileId" to childId, "emoji" to "👍"))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("TOO_MANY"))
    }

    @Test
    fun `토큰 없는 요청은 401 이고 리프레시는 리프레시 토큰만 받는다`() {
        mvc
            .perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        mvc
            .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

        val parent = devLogin()
        createFamily(parent)

        val refreshed =
            read(
                mvc
                    .perform(post("/api/v1/auth/refresh").json(mapOf("refreshToken" to parent.refreshToken)))
                    .andExpect(status().isOk),
            )
        assertThat(refreshed["userId"].asString()).isEqualTo(parent.userId.toString())
        assertThat(refreshed["nextStep"].asString()).isEqualTo("HOME")
        assertThat(refreshed["profiles"].size()).isEqualTo(1)
        mvc
            .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshed["accessToken"].asString()))
            .andExpect(status().isOk)

        mvc
            .perform(post("/api/v1/auth/refresh").json(mapOf("refreshToken" to parent.bearer.removePrefix("Bearer "))))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
        mvc
            .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + parent.refreshToken))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

        // TestAuth 가 만든 토큰도 같은 경로로 통한다.
        mvc
            .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, testAuth.bearer(parent.userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(parent.userId.toString()))
    }

    @Test
    fun `필수 누락과 형식 오류는 400 BAD_REQUEST`() {
        mvc
            .perform(post("/api/v1/auth/dev-login").json(mapOf("email" to "x@example.com")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
        mvc
            .perform(post("/api/v1/auth/google").json(mapOf("authorizationCode" to "", "redirectUri" to "https://app")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))

        val parent = devLogin()
        mvc
            .perform(
                post("/api/v1/families").auth(parent).json(
                    mapOf(
                        "familyName" to "",
                        "owner" to mapOf("name" to "엄마", "birthDate" to "1988-03-01", "sex" to "F"),
                    ),
                ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
        mvc
            .perform(
                post("/api/v1/families").auth(parent).json(
                    mapOf(
                        "familyName" to "가",
                        "owner" to mapOf("name" to "엄마", "birthDate" to "2999-01-01", "sex" to "F"),
                    ),
                ),
            ).andExpect(status().isBadRequest)
        mvc
            .perform(
                post("/api/v1/families").auth(parent).json(
                    mapOf(
                        "familyName" to "가",
                        "owner" to mapOf("name" to "엄마", "birthDate" to "1988-03-01", "sex" to "X"),
                    ),
                ),
            ).andExpect(status().isBadRequest)
        mvc
            .perform(post("/api/v1/families").auth(parent).json("{not json"))
            .andExpect(status().isBadRequest)
        mvc
            .perform(post("/api/v1/profiles/claim").auth(parent).json(mapOf("claimCode" to " ")))
            .andExpect(status().isBadRequest)
        mvc
            .perform(post("/api/v1/profiles/not-a-uuid/invite").auth(parent))
            .andExpect(status().isBadRequest)
    }
}
