package kr.ac.kookmin.familyfitness.shared.ai

import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

/** `app.ai.mode=http` 면 [HttpAiGateway] 하나만 뜨고, 닿지 않는 주소는 503 으로 번역된다. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.ai.mode=http", "app.ai.base-url=http://127.0.0.1:1"])
class AiGatewayWiringTest {
    @Autowired lateinit var gateways: List<AiGateway>

    @MockitoBean lateinit var profileQuery: ProfileQuery

    @MockitoBean lateinit var familyAccess: FamilyAccess

    @MockitoBean lateinit var cheerQuery: CheerQuery

    @MockitoBean lateinit var fitnessQuery: FitnessQuery

    @MockitoBean lateinit var activityRecorder: ActivityRecorder

    @MockitoBean lateinit var activityQuery: ActivityQuery

    @Test
    fun `http 모드에서는 HttpAiGateway 만 등록되고 연결 실패는 TEMPORARILY_UNAVAILABLE 이다`() {
        assertThat(gateways).hasSize(1)
        assertThat(gateways.single()).isInstanceOf(HttpAiGateway::class.java)

        val e = assertThrows<AiUnavailableException> { gateways.single().ask(CoachMessageRequest("p_x", "유소년", "질문")) }
        assertThat(e.code).isEqualTo("TEMPORARILY_UNAVAILABLE")
    }
}
