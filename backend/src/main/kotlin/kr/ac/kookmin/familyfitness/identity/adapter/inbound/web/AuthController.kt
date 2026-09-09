package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.identity.application.AuthService
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 인증. `/api/v1/auth/` 아래는 토큰 없이 열려 있고 `/me` 는 액세스 토큰이 필요하다. */
@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val auth: AuthService,
) {
    @PostMapping("/auth/google")
    fun google(
        @Valid @RequestBody request: GoogleLoginRequest,
    ): AuthResponse = AuthResponse.of(auth.loginWithGoogle(request.authorizationCode, request.redirectUri, request.claimCode))

    @PostMapping("/auth/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): AuthResponse = AuthResponse.of(auth.refresh(request.refreshToken))

    @GetMapping("/me")
    fun me(user: CurrentUser): MeResponse = MeResponse.of(auth.session(user.userId))
}

/** 개발용 로그인. `app.auth.dev-login.enabled=true`(local/compose/test) 일 때만 빈으로 등록된다. */
@RestController
@ConditionalOnProperty(name = ["app.auth.dev-login.enabled"], havingValue = "true")
class DevLoginController(
    private val auth: AuthService,
) {
    @PostMapping("/api/v1/auth/dev-login")
    fun devLogin(
        @Valid @RequestBody request: DevLoginRequest,
    ): AuthResponse = AuthResponse.of(auth.devLogin(request.providerUserId, request.email, request.claimCode))
}
