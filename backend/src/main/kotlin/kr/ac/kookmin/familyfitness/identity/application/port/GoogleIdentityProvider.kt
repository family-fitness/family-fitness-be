package kr.ac.kookmin.familyfitness.identity.application.port

import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind

/** 구글이 검증해 준 사용자. `subject` 가 providerUserId 가 된다. */
data class GoogleIdentity(
    val subject: String,
    val email: String?,
)

/** 구글 인가코드 → id_token 교환·검증. 네트워크는 어댑터가 맡고 application 은 결과만 본다. */
interface GoogleIdentityProvider {
    fun exchange(
        authorizationCode: String,
        redirectUri: String,
    ): GoogleIdentity
}

class GoogleAuthFailedException(
    message: String = "구글 인증에 실패했습니다",
    cause: Throwable? = null,
) : DomainException("GOOGLE_AUTH_FAILED", ErrorKind.UNAUTHORIZED, message, cause)
