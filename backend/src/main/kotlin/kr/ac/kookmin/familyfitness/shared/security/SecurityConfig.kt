package kr.ac.kookmin.familyfitness.shared.security

import jakarta.servlet.http.HttpServletResponse
import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import kr.ac.kookmin.familyfitness.shared.web.ApiError
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        jsonMapper: JsonMapper,
        props: AppProperties,
    ): SecurityFilterChain {
        val entryPoint =
            AuthenticationEntryPoint { _, response, _ ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다", jsonMapper)
            }
        val deniedHandler =
            AccessDeniedHandler { _, response, _ ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "권한이 없습니다", jsonMapper)
            }
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { it.frameOptions { f -> f.sameOrigin() } }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(*PUBLIC_PATHS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer {
                it.jwt { jwt -> jwt.decoder(jwtDecoder) }
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(deniedHandler)
            }.exceptionHandling {
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(deniedHandler)
            }
        val autoLogin = props.auth.devAutoLogin
        if (autoLogin.enabled) {
            require(autoLogin.userId.isNotBlank()) { "app.auth.dev-auto-login.user-id 가 비어 있다" }
            http.addFilterBefore(DevAutoLoginFilter(UUID.fromString(autoLogin.userId)), BearerTokenAuthenticationFilter::class.java)
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(props: AppProperties): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                if (props.cors.allowAll) allowedOriginPatterns = listOf("*") else allowedOrigins = props.cors.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                exposedHeaders = listOf("Location")
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }

    @Bean
    fun currentUserWebMvcConfigurer(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(CurrentUserArgumentResolver())
            }
        }

    private fun writeError(
        response: HttpServletResponse,
        status: Int,
        code: String,
        message: String,
        mapper: JsonMapper,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(mapper.writeValueAsString(ApiError.of(code, message)))
    }

    companion object {
        val PUBLIC_PATHS =
            arrayOf(
                "/api/v1/auth/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/actuator/health",
                "/actuator/health/**",
                "/h2-console/**",
                "/error",
            )
    }
}
