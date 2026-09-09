package kr.ac.kookmin.familyfitness.identity.adapter.outbound.google

import kr.ac.kookmin.familyfitness.identity.application.port.GoogleAuthFailedException
import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant

/** 토큰 교환은 MockRestServiceServer, id_token 서명 검증은 가짜 디코더. 네트워크에 나가지 않는다. */
class GoogleOAuthAdapterTest {
    private val props =
        AppProperties(
            auth =
                AppProperties.Auth(
                    google =
                        AppProperties.Google(
                            clientId = "client-id",
                            clientSecret = "client-secret",
                            tokenUri = "https://oauth2.example/token",
                        ),
                ),
        )
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()

    private var decoded: Jwt? = jwt(issuer = "https://accounts.google.com", audience = "client-id")
    private val decoder =
        JwtDecoder { token ->
            assertThat(token).isEqualTo("ID_TOKEN")
            decoded ?: throw BadJwtException("bad signature")
        }
    private val adapter = GoogleOAuthAdapter(builder, props, decoder)

    private fun jwt(
        issuer: String,
        audience: String,
        subject: String = "google-sub-1",
    ): Jwt =
        Jwt
            .withTokenValue("ID_TOKEN")
            .header("alg", "RS256")
            .issuer(issuer)
            .audience(listOf(audience))
            .subject(subject)
            .claim("email", "parent@example.com")
            .issuedAt(Instant.parse("2026-09-08T10:00:00Z"))
            .expiresAt(Instant.parse("2026-09-08T11:00:00Z"))
            .build()

    private fun tokenEndpointReturns(body: String) {
        server
            .expect(requestTo("https://oauth2.example/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formData(expectedForm()))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    private fun expectedForm() =
        org.springframework.util.LinkedMultiValueMap<String, String>().apply {
            add("code", "AUTH_CODE")
            add("client_id", "client-id")
            add("client_secret", "client-secret")
            add("redirect_uri", "https://app.example.com/oauth")
            add("grant_type", "authorization_code")
        }

    @Test
    fun `인가코드를 교환하고 id_token 의 sub·email 을 돌려준다`() {
        tokenEndpointReturns("""{"access_token":"x","id_token":"ID_TOKEN","token_type":"Bearer"}""")

        val identity = adapter.exchange("AUTH_CODE", "https://app.example.com/oauth")

        assertThat(identity.subject).isEqualTo("google-sub-1")
        assertThat(identity.email).isEqualTo("parent@example.com")
        server.verify()
    }

    @Test
    fun `accounts_google_com 발급자도 받는다`() {
        decoded = jwt(issuer = "accounts.google.com", audience = "client-id")
        tokenEndpointReturns("""{"id_token":"ID_TOKEN"}""")

        assertThat(adapter.exchange("AUTH_CODE", "https://app.example.com/oauth").subject).isEqualTo("google-sub-1")
    }

    @Test
    fun `토큰 교환이 4xx 면 GOOGLE_AUTH_FAILED`() {
        server
            .expect(requestTo("https://oauth2.example/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("""{"error":"invalid_grant"}""").contentType(MediaType.APPLICATION_JSON))

        val e = assertThrows<GoogleAuthFailedException> { adapter.exchange("AUTH_CODE", "https://app.example.com/oauth") }
        assertThat(e.code).isEqualTo("GOOGLE_AUTH_FAILED")
    }

    @Test
    fun `응답에 id_token 이 없으면 실패`() {
        tokenEndpointReturns("""{"access_token":"x"}""")

        assertThrows<GoogleAuthFailedException> { adapter.exchange("AUTH_CODE", "https://app.example.com/oauth") }
    }

    @Test
    fun `서명 검증 실패·발급자 불일치·대상 불일치는 모두 실패`() {
        decoded = null
        tokenEndpointReturns("""{"id_token":"ID_TOKEN"}""")
        assertThrows<GoogleAuthFailedException> { adapter.exchange("AUTH_CODE", "https://app.example.com/oauth") }

        server.reset()
        decoded = jwt(issuer = "https://evil.example", audience = "client-id")
        tokenEndpointReturns("""{"id_token":"ID_TOKEN"}""")
        assertThrows<GoogleAuthFailedException> { adapter.exchange("AUTH_CODE", "https://app.example.com/oauth") }

        server.reset()
        decoded = jwt(issuer = "https://accounts.google.com", audience = "other-client")
        tokenEndpointReturns("""{"id_token":"ID_TOKEN"}""")
        assertThrows<GoogleAuthFailedException> { adapter.exchange("AUTH_CODE", "https://app.example.com/oauth") }
    }
}
