package kr.ac.kookmin.familyfitness.shared.security

import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/**
 * 인증된 계정. 컨트롤러 파라미터로 선언하면 JWT `sub` 에서 채워진다.
 * actor(계정)와 대상 profileId 는 다르다 — 부모가 아이 기록을 대리 입력한다.
 */
data class CurrentUser(
    val userId: UUID,
)

class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == CurrentUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val auth =
            SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
                ?: throw org.springframework.security.authentication
                    .InsufficientAuthenticationException("인증이 필요합니다")
        return CurrentUser(UUID.fromString(auth.token.subject))
    }
}
