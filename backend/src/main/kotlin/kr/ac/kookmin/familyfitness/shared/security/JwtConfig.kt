package kr.ac.kookmin.familyfitness.shared.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.spec.SecretKeySpec

/**
 * 서비스 토큰(HS256). 액세스 토큰만 API 인증에 쓰이고, 리프레시 토큰은 `token_use=refresh` 라 거부된다.
 * 발급은 identity 모듈의 인증 어댑터가, 검증은 리소스 서버 필터가 한다.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {
    @Bean
    fun jwtSecretKey(props: AppProperties): SecretKeySpec {
        val secret = props.auth.jwt.secret
        require(secret.length >= 32) { "app.auth.jwt.secret 은 32자 이상이어야 한다 (APP_JWT_SECRET)" }
        return SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(key: SecretKeySpec): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))

    @Bean
    fun jwtDecoder(
        key: SecretKeySpec,
        props: AppProperties,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(props.auth.jwt.issuer),
                AccessTokenOnlyValidator,
            ),
        )
        return decoder
    }

    private object AccessTokenOnlyValidator : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (token.getClaimAsString(ServiceTokens.TOKEN_USE_CLAIM) == ServiceTokens.ACCESS) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "액세스 토큰이 아니다", null))
            }
    }
}

/** 토큰 클레임 이름과 값. 발급(identity)과 검증(shared)이 같은 상수를 본다. */
object ServiceTokens {
    const val TOKEN_USE_CLAIM = "token_use"
    const val ACCESS = "access"
    const val REFRESH = "refresh"
}
