package kr.ac.kookmin.familyfitness.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 서비스 토큰(HS256). 액세스 토큰만 API 인증에 쓰이고, 리프레시 토큰은 `token_use=refresh` 라 거부된다.
 * 발급은 identity 모듈의 인증 어댑터가, 검증은 리소스 서버 필터가 한다.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {
    @Bean
    public SecretKeySpec jwtSecretKey(AppProperties props) {
        String secret = props.auth().jwt().secret();
        if (secret.length() < 32) {
            throw new IllegalArgumentException("app.auth.jwt.secret 은 32자 이상이어야 한다 (APP_JWT_SECRET)");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKeySpec key, AppProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.auth().jwt().issuer()), AccessTokenOnlyValidator.INSTANCE));
        return decoder;
    }

    private static final class AccessTokenOnlyValidator implements OAuth2TokenValidator<Jwt> {
        private static final AccessTokenOnlyValidator INSTANCE = new AccessTokenOnlyValidator();

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return TokenClaims.ACCESS.equals(token.getClaimAsString(TokenClaims.TOKEN_USE_CLAIM))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "액세스 토큰이 아니다", null));
        }
    }
}
