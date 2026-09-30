package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import kr.ac.kookmin.familyfitness.identity.application.AuthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 개발용 로그인. `app.auth.dev-login.enabled=true`(local/compose/test) 일 때만 빈으로 등록된다. */
@RestController
@ConditionalOnProperty(name = "app.auth.dev-login.enabled", havingValue = "true")
public class DevLoginController {
    private final AuthService auth;

    public DevLoginController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/v1/auth/dev-login")
    public AuthResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        return AuthResponse.of(auth.devLogin(request.providerUserId(), request.email(), request.claimCode()));
    }
}
