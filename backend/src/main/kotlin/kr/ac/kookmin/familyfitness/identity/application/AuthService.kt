package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository
import kr.ac.kookmin.familyfitness.identity.domain.User
import kr.ac.kookmin.familyfitness.shared.security.InvalidRefreshTokenException
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokens
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 로그인 뒤 프론트가 갈 화면. 프로필 0개 + 코드 없음 → 가족 만들기, 0개 + 코드 있음 → 초대 코드 입력, 있음 → 홈. */
enum class NextStep {
    CREATE_FAMILY,
    CLAIM,
    HOME,
    SUPPORT_MODE,
    ;

    companion object {
        fun afterLogin(
            profiles: List<ProfileSummary>,
            claimCode: String?,
        ): NextStep =
            when {
                profiles.isNotEmpty() -> HOME
                claimCode.isNullOrBlank() -> CREATE_FAMILY
                else -> CLAIM
            }
    }
}

/** 계정과 그 계정에 붙은 프로필들. `/me` 와 인증 응답이 공유하는 모양. */
data class AuthSession(
    val userId: UUID,
    val nextStep: NextStep,
    val profiles: List<ProfileSummary>,
)

data class AuthResult(
    val tokens: ServiceTokens,
    val session: AuthSession,
)

/** 구글 로그인·개발용 로그인·리프레시·내 정보. 계정 병합 경로는 없다(provider 하나로 고정). */
@Service
@Transactional
class AuthService(
    private val registration: UserRegistrationService,
    private val google: GoogleIdentityProvider,
    private val users: UserRepository,
    private val families: FamilyRepository,
    private val summaries: ProfileSummaries,
    private val tokenIssuer: ServiceTokenIssuer,
) {
    fun loginWithGoogle(
        authorizationCode: String,
        redirectUri: String,
        claimCode: String?,
    ): AuthResult {
        val identity = google.exchange(authorizationCode, redirectUri)
        val user = registration.registerOrGet(User.PROVIDER_GOOGLE, identity.subject, identity.email)
        return issue(user.id, claimCode)
    }

    /** local/compose/test 전용. 컨트롤러가 `app.auth.dev-login.enabled` 로만 열린다. */
    fun devLogin(
        providerUserId: String,
        email: String?,
        claimCode: String?,
    ): AuthResult {
        val user = registration.registerOrGet(User.PROVIDER_DEV, providerUserId, email)
        return issue(user.id, claimCode)
    }

    fun refresh(refreshToken: String): AuthResult {
        val userId = tokenIssuer.userIdOfRefreshToken(refreshToken)
        users.findById(userId) ?: throw InvalidRefreshTokenException("계정이 없습니다")
        return issue(userId, claimCode = null)
    }

    @Transactional(readOnly = true)
    fun session(
        userId: UUID,
        claimCode: String? = null,
    ): AuthSession {
        val profiles = families.profilesOfUser(userId).map(summaries::summary)
        return AuthSession(userId, NextStep.afterLogin(profiles, claimCode), profiles)
    }

    private fun issue(
        userId: UUID,
        claimCode: String?,
    ): AuthResult = AuthResult(tokenIssuer.issue(userId), session(userId, claimCode))
}
