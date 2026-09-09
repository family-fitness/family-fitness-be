package kr.ac.kookmin.familyfitness.support

import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer
import org.springframework.stereotype.Component
import java.util.UUID

/** 웹 테스트용 Bearer 헤더 값. 실제 발급 경로([ServiceTokenIssuer])를 그대로 쓴다. */
@Component
class TestAuth(
    private val issuer: ServiceTokenIssuer,
) {
    fun bearer(userId: UUID): String = "Bearer " + issuer.issue(userId).accessToken
}
