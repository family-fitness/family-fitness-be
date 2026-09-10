package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository
import kr.ac.kookmin.familyfitness.identity.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 소셜 인증을 마친 사용자의 최초 가입 또는 기존 계정 조회(find-or-create).
 * provider·providerUserId 는 서버가 검증한 인증 결과에서만 온다.
 */
@Service
@Transactional
class UserRegistrationService(
    private val repository: UserRepository,
) {
    fun registerOrGet(
        provider: String,
        providerUserId: String,
        email: String?,
    ): User {
        repository.findByProviderAndProviderUserId(provider, providerUserId)?.let { return it }
        val user = User.register(provider, providerUserId, email)
        repository.save(user)
        return user
    }
}
