package kr.ac.kookmin.familyfitness.identity.application.port

import kr.ac.kookmin.familyfitness.identity.domain.User
import java.util.UUID

interface UserRepository {
    fun findById(id: UUID): User?

    fun findByProviderAndProviderUserId(
        provider: String,
        providerUserId: String,
    ): User?

    fun save(user: User): User
}
