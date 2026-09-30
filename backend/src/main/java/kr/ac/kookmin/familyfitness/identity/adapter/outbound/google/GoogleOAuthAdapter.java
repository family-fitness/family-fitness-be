package kr.ac.kookmin.familyfitness.identity.adapter.outbound.google;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleAuthFailedException;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구글 인가코드 교환(`token-uri`) → `id_token` 서명·시각 검증(`jwk-set-uri`) → 발급자·대상 확인 → sub/email.
 * 어떤 단계든 실패하면 `GOOGLE_AUTH_FAILED`(401). 검증기는 테스트에서 가짜로 바꿀 수 있게 생성자로 받는다.
 */
@Component
public class GoogleOAuthAdapter implements GoogleIdentityProvider {
    public static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final AppProperties props;
    private final JwtDecoder idTokenDecoder;
    private final RestClient restClient;

    public GoogleOAuthAdapter(RestClient.Builder restClientBuilder, AppProperties props, JwtDecoder idTokenDecoder) {
        this.props = props;
        this.idTokenDecoder = idTokenDecoder;
        this.restClient = restClientBuilder.build();
    }

    /** 운영 생성자. 서비스 토큰용 {@link JwtDecoder} 빈이 잘못 주입되지 않도록 구글 JWK 검증기를 여기서 만든다. */
    @Autowired
    public GoogleOAuthAdapter(RestClient.Builder restClientBuilder, AppProperties props, Clock clock) {
        this(restClientBuilder, props, defaultDecoder(props, clock));
    }

    @Override
    public GoogleIdentity exchange(String authorizationCode, String redirectUri) {
        String idToken = exchangeCode(authorizationCode, redirectUri);
        Jwt jwt = decode(idToken);
        validate(jwt);
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) throw new GoogleAuthFailedException("id_token 에 sub 가 없습니다");
        return new GoogleIdentity(subject, jwt.getClaimAsString("email"));
    }

    private String exchangeCode(String authorizationCode, String redirectUri) {
        AppProperties.Google google = props.auth().google();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", authorizationCode);
        form.add("client_id", google.clientId());
        form.add("client_secret", google.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        Map<String, @Nullable Object> body;
        try {
            body = restClient
                    .post()
                    .uri(google.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, @Nullable Object>>() {});
        } catch (RestClientException e) {
            throw new GoogleAuthFailedException("구글 토큰 교환에 실패했습니다", e);
        }
        Object idToken = body == null ? null : body.get("id_token");
        if (!(idToken instanceof String value)) throw new GoogleAuthFailedException("구글 응답에 id_token 이 없습니다");
        return value;
    }

    private Jwt decode(String idToken) {
        try {
            return idTokenDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new GoogleAuthFailedException("id_token 검증에 실패했습니다", e);
        }
    }

    private void validate(Jwt jwt) {
        // `Jwt.issuer` 는 URL 변환을 시도해 `accounts.google.com` 에서 예외를 던지므로 문자열로 본다.
        String issuer = jwt.getClaimAsString(JwtClaimNames.ISS);
        if (!ISSUERS.contains(issuer)) throw new GoogleAuthFailedException("id_token 발급자가 다릅니다: " + issuer);
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(props.auth().google().clientId())) {
            throw new GoogleAuthFailedException("id_token 대상이 다릅니다");
        }
    }

    private static JwtDecoder defaultDecoder(AppProperties props, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                        props.auth().google().jwkSetUri())
                .build();
        JwtTimestampValidator validator = new JwtTimestampValidator();
        validator.setClock(clock);
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
