package kr.ac.kookmin.familyfitness.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.UUID

/**
 * 로컬 시연용 자동 로그인. `Authorization` 헤더가 없는 요청을 [defaultUserId](시드 데모 부모)로 인증한다.
 * `X-Dev-User-Id: <uuid>` 헤더로 다른 계정(예: 데모 두 번째 부모 …0002)이 될 수 있다.
 * `app.auth.dev-auto-login.enabled=true`(local/compose)일 때만 체인에 들어가며, 운영에서는 존재하지 않는다.
 * Bearer 토큰을 보내면 평소처럼 토큰이 우선한다 — 프론트가 로그인 흐름을 붙인 뒤에도 그대로 동작한다.
 */
class DevAutoLoginFilter(
    private val defaultUserId: UUID,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION).isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            val userId =
                request
                    .getHeader(DEV_USER_HEADER)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(UUID::fromString) ?: defaultUserId
            val jwt =
                Jwt
                    .withTokenValue("dev-auto-login")
                    .header("alg", "none")
                    .subject(userId.toString())
                    .claim(TokenClaims.TOKEN_USE_CLAIM, TokenClaims.ACCESS)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build()
            val context = SecurityContextHolder.createEmptyContext().apply { authentication = JwtAuthenticationToken(jwt, emptyList()) }
            SecurityContextHolder.setContext(context)
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val DEV_USER_HEADER = "X-Dev-User-Id"
    }
}
