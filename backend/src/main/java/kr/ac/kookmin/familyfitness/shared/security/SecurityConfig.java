package kr.ac.kookmin.familyfitness.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.web.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {
    public static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/actuator/health/**",
        "/h2-console/**",
        "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtDecoder jwtDecoder, JsonMapper jsonMapper, AppProperties props) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다", jsonMapper);
        AccessDeniedHandler deniedHandler = (request, response, exception) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "권한이 없습니다", jsonMapper);
        http.csrf(it -> it.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(it -> it.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(it -> it.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(it -> it.requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(it -> {
                    it.jwt(jwt -> jwt.decoder(jwtDecoder));
                    it.authenticationEntryPoint(entryPoint);
                    it.accessDeniedHandler(deniedHandler);
                })
                .exceptionHandling(it -> {
                    it.authenticationEntryPoint(entryPoint);
                    it.accessDeniedHandler(deniedHandler);
                });
        AppProperties.DevAutoLogin autoLogin = props.auth().devAutoLogin();
        if (autoLogin.enabled()) {
            if (autoLogin.userId().isBlank()) {
                throw new IllegalArgumentException("app.auth.dev-auto-login.user-id 가 비어 있다");
            }
            http.addFilterBefore(
                    new DevAutoLoginFilter(UUID.fromString(autoLogin.userId())), BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        if (props.cors().allowAll()) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(props.cors().allowedOrigins());
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public WebMvcConfigurer currentUserWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }

    private void writeError(HttpServletResponse response, int status, String code, String message, JsonMapper mapper)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ApiError.of(code, message)));
    }
}
