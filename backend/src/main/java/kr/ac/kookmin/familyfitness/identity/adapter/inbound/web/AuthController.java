package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import kr.ac.kookmin.familyfitness.identity.application.AuthService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증. `/api/v1/auth/` 아래는 토큰 없이 열려 있고 `/me` 는 액세스 토큰이 필요하다. */
@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return AuthResponse.of(
                auth.loginWithGoogle(request.authorizationCode(), request.redirectUri(), request.claimCode()));
    }

    /** 새 토큰 쌍을 주고, 받은 리프레시 토큰은 폐기한다. 폐기된 토큰을 다시 보내면 그 묶음 전체가 폐기되고 401 이다. */
    @PostMapping("/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthResponse.of(auth.refresh(request.refreshToken()));
    }

    /**
     * 이 리프레시 토큰이 속한 로그인 묶음을 폐기한다. 본문이 없거나 · 토큰이 비었거나 · 모르는 토큰이어도 204 다.
     * 공개 경로(`/api/v1/auth/**`)라 액세스 토큰이 끝난 뒤에도 부를 수 있다.
     */
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) @Nullable LogoutRequest request) {
        auth.logout(request == null ? null : request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(CurrentUser user) {
        return MeResponse.of(auth.session(user.userId()));
    }
}
