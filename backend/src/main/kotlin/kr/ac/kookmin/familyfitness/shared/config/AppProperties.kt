package kr.ac.kookmin.familyfitness.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app")
data class AppProperties(
    val timezone: String = "Asia/Seoul",
    val frontendBaseUrl: String = "http://localhost:5173",
    val cors: Cors = Cors(),
    val auth: Auth = Auth(),
    val ai: Ai = Ai(),
) {
    data class Cors(
        val allowedOrigins: List<String> = emptyList(),
    )

    data class Auth(
        val jwt: Jwt = Jwt(),
        val devLogin: DevLogin = DevLogin(),
        val google: Google = Google(),
    )

    data class Jwt(
        val issuer: String = "familyfitness",
        /** HS256 대칭키. 32바이트 이상. 운영에서는 APP_JWT_SECRET 환경변수로 주입한다. */
        val secret: String = "",
        val accessTtl: Duration = Duration.ofHours(1),
        val refreshTtl: Duration = Duration.ofDays(30),
    )

    /** local 프로필 전용 개발 로그인. 운영 로그인 수단이 아니다. */
    data class DevLogin(
        val enabled: Boolean = false,
    )

    data class Google(
        val clientId: String = "",
        val clientSecret: String = "",
        val tokenUri: String = "https://oauth2.googleapis.com/token",
        val jwkSetUri: String = "https://www.googleapis.com/oauth2/v3/certs",
    )

    /** `stub`: AI 서비스 없이 결정적 가짜 응답 · `http`: FastAPI 내부망 호출 */
    data class Ai(
        val mode: String = "stub",
        val baseUrl: String = "http://localhost:8000",
    ) {
        val isStub: Boolean get() = mode.equals("stub", ignoreCase = true)
    }
}
