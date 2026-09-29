package kr.ac.kookmin.familyfitness.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬 시연용 자동 로그인. `X-Dev-User-Id: <uuid>` 헤더를 보낸 요청만 그 계정(예: 데모 부모 …0001)으로 인증한다 — curl · 시나리오
 * 스크립트용이다. 헤더가 없거나 비어 있으면 아무것도 하지 않아서 운영과 똑같이 401 이 되고, 브라우저는 로그인 화면으로 간다(처음 쓰는
 * 사람의 흐름을 로컬에서도 그대로 본다). 브라우저로 들어가려면 로그인 화면의 개발용 계정(`POST /api/v1/auth/dev-login`)을 쓴다.
 * `app.auth.dev-auto-login.enabled=true`(local/compose)일 때만 체인에 들어가며, 운영에서는 존재하지 않는다.
 * local · compose · test 가 아닌 프로필에서 켜면 {@link DevFeatureGuard} 가 기동을 멈춘다.
 * Bearer 토큰을 보내면 평소처럼 토큰이 우선한다.
 */
public class DevAutoLoginFilter extends OncePerRequestFilter {
    public static final String DEV_USER_HEADER = "X-Dev-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String devUser = request.getHeader(DEV_USER_HEADER);
        if ((authorization == null || authorization.isBlank())
                && devUser != null
                && !devUser.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UUID userId;
            try {
                userId = UUID.fromString(devUser.trim());
            } catch (IllegalArgumentException e) {
                // UUID 가 아닌 헤더는 호출자 잘못이다. sendError 는 /error 로 넘어가 400 BAD_REQUEST 봉투로 나간다.
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
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
}
