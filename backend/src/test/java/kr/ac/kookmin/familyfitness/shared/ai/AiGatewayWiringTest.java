package kr.ac.kookmin.familyfitness.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** `app.ai.mode=http` 면 [HttpAiGateway] 하나만 뜨고, 닿지 않는 주소는 503 으로 번역된다. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.ai.mode=http", "app.ai.base-url=http://127.0.0.1:1"})
class AiGatewayWiringTest {
    @Autowired
    List<AiGateway> gateways;

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

    @Test
    @DisplayName("http 모드에서는 HttpAiGateway 만 등록되고 연결 실패는 TEMPORARILY_UNAVAILABLE 이다")
    void http_모드에서는_HttpAiGateway_만_등록되고_연결_실패는_TEMPORARILY_UNAVAILABLE_이다() {
        assertThat(gateways).hasSize(1);
        assertThat(gateways.getFirst()).isInstanceOf(HttpAiGateway.class);

        AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> gateways.getFirst()
                .ask(new CoachMessageRequest("p_x", "유소년", "질문")));
        assertThat(e.getCode()).isEqualTo("TEMPORARILY_UNAVAILABLE");
    }
}
