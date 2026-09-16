package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import kr.ac.kookmin.familyfitness.identity.application.AuthService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthResponse.of(auth.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    public MeResponse me(CurrentUser user) {
        return MeResponse.of(auth.session(user.userId()));
    }
}
