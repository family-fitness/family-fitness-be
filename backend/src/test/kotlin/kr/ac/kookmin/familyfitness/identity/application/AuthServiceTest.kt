package kr.ac.kookmin.familyfitness.identity.application

import com.nimbusds.jose.jwk.source.ImmutableSecret
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleAuthFailedException
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider
import kr.ac.kookmin.familyfitness.identity.domain.User
import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.security.InvalidRefreshTokenException
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class AuthServiceTest {
    private val clock = MutableClock(Instant.parse("2026-09-08T10:00:00Z"))
    private val identityClock = clock.identityClock()
    private val users = InMemoryUserRepository()
    private val families = InMemoryFamilyRepository()
    private val summaries = ProfileSummaries(identityClock)
    private val props =
        AppProperties(
            auth = AppProperties.Auth(jwt = AppProperties.Jwt(secret = "test-only-secret-test-only-secret-0123456789")),
        )
    private val key =
        SecretKeySpec(
            props.auth.jwt.secret
                .toByteArray(),
            "HmacSHA256",
        )
    private val tokenIssuer = ServiceTokenIssuer(NimbusJwtEncoder(ImmutableSecret(key)), key, props, clock)

    private val google =
        object : GoogleIdentityProvider {
            var next: GoogleIdentity? = GoogleIdentity("google-sub-1", "parent@example.com")

            override fun exchange(
                authorizationCode: String,
                redirectUri: String,
            ): GoogleIdentity = next ?: throw GoogleAuthFailedException()
        }

    private val service = AuthService(UserRegistrationService(users), google, users, families, summaries, tokenIssuer)
    private val familyService = FamilyService(families, summaries, identityClock)

    @Test
    fun `구글 첫 로그인은 계정을 만들고 프로필이 없으면 CREATE_FAMILY 로 보낸다`() {
        val result = service.loginWithGoogle("code", "https://app/redirect", claimCode = null)

        assertThat(result.tokens.accessToken).isNotBlank()
        assertThat(result.tokens.refreshToken).isNotBlank()
        assertThat(result.session.profiles).isEmpty()
        assertThat(result.session.nextStep).isEqualTo(NextStep.CREATE_FAMILY)
        val user = users.users.values.single()
        assertThat(user.provider).isEqualTo(User.PROVIDER_GOOGLE)
        assertThat(user.providerUserId).isEqualTo("google-sub-1")
        assertThat(user.email).isEqualTo("parent@example.com")
        assertThat(result.session.userId).isEqualTo(user.id)
    }

    @Test
    fun `초대 코드를 들고 온 새 계정은 CLAIM 으로, 프로필이 있으면 HOME 으로 보낸다`() {
        val withCode = service.loginWithGoogle("code", "https://app/redirect", claimCode = "ABC234")
        assertThat(withCode.session.nextStep).isEqualTo(NextStep.CLAIM)

        familyService.createFamily(withCode.session.userId, "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F)

        val again = service.loginWithGoogle("code", "https://app/redirect", claimCode = "ABC234")
        assertThat(again.session.userId).isEqualTo(withCode.session.userId)
        assertThat(again.session.nextStep).isEqualTo(NextStep.HOME)
        assertThat(again.session.profiles).hasSize(1)
        assertThat(users.users).hasSize(1)
    }

    @Test
    fun `구글 인증 실패는 그대로 올라간다`() {
        google.next = null

        assertThrows<GoogleAuthFailedException> { service.loginWithGoogle("bad", "https://app/redirect", null) }
        assertThat(users.users).isEmpty()
    }

    @Test
    fun `개발용 로그인은 DEV 제공자로 계정을 만든다`() {
        val result = service.devLogin("dev-parent", null, null)

        val user = users.users.values.single()
        assertThat(user.provider).isEqualTo(User.PROVIDER_DEV)
        assertThat(user.providerUserId).isEqualTo("dev-parent")
        assertThat(result.session.nextStep).isEqualTo(NextStep.CREATE_FAMILY)
        assertThat(service.devLogin("dev-parent", "x@example.com", null).session.userId).isEqualTo(user.id)
    }

    @Test
    fun `리프레시는 리프레시 토큰만 받고 계정이 있어야 한다`() {
        val login = service.devLogin("dev-parent", null, null)

        val refreshed = service.refresh(login.tokens.refreshToken)
        assertThat(refreshed.session.userId).isEqualTo(login.session.userId)
        assertThat(refreshed.session.nextStep).isEqualTo(NextStep.CREATE_FAMILY)

        assertThrows<InvalidRefreshTokenException> { service.refresh(login.tokens.accessToken) }
        assertThrows<InvalidRefreshTokenException> { service.refresh("not-a-token") }

        val orphan = tokenIssuer.issue(UUID.randomUUID()).refreshToken
        assertThrows<InvalidRefreshTokenException> { service.refresh(orphan) }
    }

    @Test
    fun `내 정보는 계정과 프로필 목록을 준다`() {
        val login = service.devLogin("dev-parent", null, null)
        familyService.createFamily(login.session.userId, "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F)

        val session = service.session(login.session.userId)

        assertThat(session.nextStep).isEqualTo(NextStep.HOME)
        assertThat(session.profiles.single().name).isEqualTo("엄마")
    }
}
