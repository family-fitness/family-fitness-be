package kr.ac.kookmin.familyfitness.identity.domain

import java.util.UUID

enum class UserStatus {
    ACTIVE,
    INACTIVE,
}

/**
 * 로그인 계정. OAuth 제공자의 식별자(provider, providerUserId)로만 연결하며 사람(Profile)과 다르다.
 * 이메일이 같아도 제공자 식별자가 다르면 별도 계정이다 — 계정 병합 경로는 없다.
 */
data class User(
    val id: UUID,
    val provider: String,
    val providerUserId: String,
    val email: String?,
    val status: UserStatus = UserStatus.ACTIVE,
) {
    init {
        require(provider.isNotBlank()) { "provider 는 비어 있을 수 없다" }
        require(providerUserId.isNotBlank()) { "providerUserId 는 비어 있을 수 없다" }
    }

    companion object {
        const val PROVIDER_GOOGLE = "GOOGLE"
        const val PROVIDER_DEV = "DEV"

        fun register(
            provider: String,
            providerUserId: String,
            email: String?,
        ): User = User(UUID.randomUUID(), provider, providerUserId, email)
    }
}
