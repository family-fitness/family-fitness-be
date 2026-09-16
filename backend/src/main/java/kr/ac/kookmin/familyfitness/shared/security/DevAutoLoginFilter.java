package kr.ac.kookmin.familyfitness.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬 시연용 자동 로그인. `Authorization` 헤더가 없는 요청을 {@code defaultUserId}(시드 데모 부모)로 인증한다.
 * `X-Dev-User-Id: <uuid>` 헤더로 다른 계정(예: 데모 두 번째 부모 …0002)이 될 수 있다.
 * `app.auth.dev-auto-login.enabled=true`(local/compose)일 때만 체인에 들어가며, 운영에서는 존재하지 않는다.
 * Bearer 토큰을 보내면 평소처럼 토큰이 우선한다 — 프론트가 로그인 흐름을 붙인 뒤에도 그대로 동작한다.
 */
public class DevAutoLoginFilter extends OncePerRequestFilter {
    public static final String DEV_USER_HEADER = "X-Dev-User-Id";

    private final UUID defaultUserId;

    public DevAutoLoginFilter(UUID defaultUserId) {
        this.defaultUserId = defaultUserId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if ((authorization == null || authorization.isBlank())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UUID userId = headerUserId(request.getHeader(DEV_USER_HEADER));
            Jwt jwt = Jwt.withTokenValue("dev-auto-login")
                    .header("alg", "none")
                    .subject(userId.toString())
                    .claim(TokenClaims.TOKEN_USE_CLAIM, TokenClaims.ACCESS)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }

    private UUID headerUserId(@Nullable String header) {
        if (header == null) return defaultUserId;
        String trimmed = header.trim();
        return trimmed.isEmpty() ? defaultUserId : UUID.fromString(trimmed);
    }
}
