package kr.ac.kookmin.familyfitness.identity.adapter.outbound.google

import kr.ac.kookmin.familyfitness.identity.application.port.GoogleAuthFailedException
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider
import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Clock

/**
 * 구글 인가코드 교환(`token-uri`) → `id_token` 서명·시각 검증(`jwk-set-uri`) → 발급자·대상 확인 → sub/email.
 * 어떤 단계든 실패하면 `GOOGLE_AUTH_FAILED`(401). 검증기는 테스트에서 가짜로 바꿀 수 있게 생성자로 받는다.
 */
@Component
class GoogleOAuthAdapter(
    restClientBuilder: RestClient.Builder,
    private val props: AppProperties,
    private val idTokenDecoder: JwtDecoder,
) : GoogleIdentityProvider {
    /** 운영 생성자. 서비스 토큰용 [JwtDecoder] 빈이 잘못 주입되지 않도록 구글 JWK 검증기를 여기서 만든다. */
    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        props: AppProperties,
        clock: Clock,
    ) : this(restClientBuilder, props, defaultDecoder(props, clock))

    private val restClient: RestClient = restClientBuilder.build()

    override fun exchange(
        authorizationCode: String,
        redirectUri: String,
    ): GoogleIdentity {
        val idToken = exchangeCode(authorizationCode, redirectUri)
        val jwt = decode(idToken)
        validate(jwt)
        val subject = jwt.subject?.takeIf { it.isNotBlank() } ?: throw GoogleAuthFailedException("id_token 에 sub 가 없습니다")
        return GoogleIdentity(subject, jwt.getClaimAsString("email"))
    }

    private fun exchangeCode(
        authorizationCode: String,
        redirectUri: String,
    ): String {
        val google = props.auth.google
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("code", authorizationCode)
                add("client_id", google.clientId)
                add("client_secret", google.clientSecret)
                add("redirect_uri", redirectUri)
                add("grant_type", "authorization_code")
            }
        val body =
            try {
                restClient
                    .post()
                    .uri(google.tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            } catch (e: RestClientException) {
                throw GoogleAuthFailedException("구글 토큰 교환에 실패했습니다", e)
            }
        return body?.get("id_token") as? String ?: throw GoogleAuthFailedException("구글 응답에 id_token 이 없습니다")
    }

    private fun decode(idToken: String): Jwt =
        try {
            idTokenDecoder.decode(idToken)
        } catch (e: JwtException) {
            throw GoogleAuthFailedException("id_token 검증에 실패했습니다", e)
        }

    private fun validate(jwt: Jwt) {
        // `Jwt.issuer` 는 URL 변환을 시도해 `accounts.google.com` 에서 예외를 던지므로 문자열로 본다.
        val issuer = jwt.getClaimAsString(JwtClaimNames.ISS)
        if (issuer !in ISSUERS) throw GoogleAuthFailedException("id_token 발급자가 다릅니다: $issuer")
        if (props.auth.google.clientId !in (jwt.audience ?: emptyList())) throw GoogleAuthFailedException("id_token 대상이 다릅니다")
    }

    companion object {
        val ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")

        private fun defaultDecoder(
            props: AppProperties,
            clock: Clock,
        ): JwtDecoder {
            val decoder = NimbusJwtDecoder.withJwkSetUri(props.auth.google.jwkSetUri).build()
            decoder.setJwtValidator(JwtTimestampValidator().apply { setClock(clock) })
            return decoder
        }
    }
}
