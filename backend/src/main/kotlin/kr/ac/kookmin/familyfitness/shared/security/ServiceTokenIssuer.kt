package kr.ac.kookmin.familyfitness.shared.security

import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

/** 발급된 토큰 쌍. */
data class ServiceTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
)

class InvalidRefreshTokenException(
    message: String = "리프레시 토큰이 유효하지 않습니다",
    cause: Throwable? = null,
) : DomainException("INVALID_REFRESH_TOKEN", ErrorKind.UNAUTHORIZED, message, cause)

/**
 * 서비스 토큰 발급·리프레시 검증. `sub` 는 계정(userId)이며 사람이 아니라 로그인 수단이다.
 * 액세스 토큰만 API 인증에 쓰이고([JwtConfig] 의 검증기), 리프레시 토큰은 이 클래스만 읽는다.
 */
@Component
class ServiceTokenIssuer(
    private val encoder: JwtEncoder,
    private val key: SecretKeySpec,
    private val props: AppProperties,
    private val clock: Clock,
) {
    private val refreshDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()

    fun issue(userId: UUID): ServiceTokens {
        val now = clock.instant()
        val accessExpiresAt = now.plus(props.auth.jwt.accessTtl)
        return ServiceTokens(
            accessToken = encode(userId, now, accessExpiresAt, TokenClaims.ACCESS),
            refreshToken =
                encode(userId, now, now.plus(props.auth.jwt.refreshTtl), TokenClaims.REFRESH),
            accessExpiresAt = accessExpiresAt,
        )
    }

    /** 리프레시 토큰을 검증하고 계정 ID 를 돌려준다. 액세스 토큰을 넣으면 거부한다. */
    fun userIdOfRefreshToken(refreshToken: String): UUID {
        val jwt =
            try {
                refreshDecoder.decode(refreshToken)
            } catch (e: JwtException) {
                throw InvalidRefreshTokenException(cause = e)
            }
        val use = jwt.getClaimAsString(TokenClaims.TOKEN_USE_CLAIM)
        if (use != TokenClaims.REFRESH) throw InvalidRefreshTokenException("리프레시 토큰이 아닙니다")
        if (jwt.issuer?.toString() != props.auth.jwt.issuer) throw InvalidRefreshTokenException("발급자가 다릅니다")
        val exp = jwt.expiresAt ?: throw InvalidRefreshTokenException("만료 시각이 없습니다")
        if (exp.isBefore(clock.instant())) throw InvalidRefreshTokenException("만료된 리프레시 토큰입니다")
        return UUID.fromString(jwt.subject)
    }

    private fun encode(
        userId: UUID,
        issuedAt: Instant,
        expiresAt: Instant,
        tokenUse: String,
    ): String {
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(props.auth.jwt.issuer)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE_CLAIM, tokenUse)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}
