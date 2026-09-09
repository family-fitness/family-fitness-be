package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository
import kr.ac.kookmin.familyfitness.identity.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.util.UUID

/**
 * 흐름 1: 소셜 인증을 마친 사용자의 최초 가입 또는 기존 계정 조회.
 * provider와 providerUserId는 서버가 검증한 인증 결과에서 가져온다.
 * Google은 테스트 예시이며 지원 제공자를 확정하는 테스트는 아니다.
 * 가족 생성과 PARENT 프로필 등록은 다음 사용자 흐름에서 다룬다.
 */
class UserRegistrationServiceTest {
    private val repository = mock(UserRepository::class.java)
    private val service = UserRegistrationService(repository)

    @Test
    fun `부모가 처음 소셜 로그인하면 새 계정을 저장한다`() {
        `when`(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-1"))
            .thenReturn(null)

        val user = service.registerOrGet("GOOGLE", "google-parent-1", "parent@example.com")

        val saved = savedUsers().single()
        assertThat(saved.id).isEqualTo(user.id)
        assertThat(user.id).isNotNull()
        assertThat(saved.provider).isEqualTo("GOOGLE")
        assertThat(saved.providerUserId).isEqualTo("google-parent-1")
        assertThat(saved.email).isEqualTo("parent@example.com")
    }

    @Test
    fun `같은 소셜 계정으로 다시 로그인하면 새로 가입시키지 않는다`() {
        val existing =
            User(
                id = UUID.randomUUID(),
                provider = "GOOGLE",
                providerUserId = "google-parent-1",
                email = "parent@example.com",
            )
        `when`(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-1"))
            .thenReturn(existing)

        val user = service.registerOrGet("GOOGLE", "google-parent-1", "parent@example.com")

        assertThat(user.id).isEqualTo(existing.id)
        assertThat(savedUsers()).isEmpty()
    }

    @Test
    fun `이메일이 같아도 서로 다른 소셜 계정은 별도 계정으로 가입한다`() {
        `when`(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-1"))
            .thenReturn(null)
        `when`(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-2"))
            .thenReturn(null)

        val first = service.registerOrGet("GOOGLE", "google-parent-1", "shared@example.com")
        val second = service.registerOrGet("GOOGLE", "google-parent-2", "shared@example.com")

        assertThat(first.id).isNotEqualTo(second.id)
        val saved = savedUsers()
        assertThat(saved).hasSize(2)
        assertThat(saved.map { it.providerUserId })
            .containsExactly("google-parent-1", "google-parent-2")
        assertThat(saved.map { it.email })
            .containsOnly("shared@example.com")
    }

    private fun savedUsers(): List<User> =
        mockingDetails(repository)
            .invocations
            .filter { it.method.name == "save" }
            .map { it.arguments[0] as User }
}
