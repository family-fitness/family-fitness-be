package kr.ac.kookmin.familyfitness.shared.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 서비스 토큰 발급·리프레시 검증. `sub` 는 계정(userId)이며 사람이 아니라 로그인 수단이다.
 * 액세스 토큰만 API 인증에 쓰이고({@link JwtConfig} 의 검증기), 리프레시 토큰은 이 클래스만 읽는다.
 */
@Component
public class ServiceTokenIssuer {
    private final JwtEncoder encoder;
    private final AppProperties props;
    private final Clock clock;
    private final NimbusJwtDecoder refreshDecoder;

    public ServiceTokenIssuer(JwtEncoder encoder, SecretKeySpec key, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
        this.refreshDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public ServiceTokens issue(UUID userId) {
        Instant now = clock.instant();
        Instant accessExpiresAt = now.plus(props.auth().jwt().accessTtl());
        return new ServiceTokens(
                encode(userId, now, accessExpiresAt, TokenClaims.ACCESS),
                encode(userId, now, now.plus(props.auth().jwt().refreshTtl()), TokenClaims.REFRESH),
                accessExpiresAt);
    }

    /** 리프레시 토큰을 검증하고 계정 ID 를 돌려준다. 액세스 토큰을 넣으면 거부한다. */
    public UUID userIdOfRefreshToken(String refreshToken) {
        Jwt jwt;
        try {
            jwt = refreshDecoder.decode(refreshToken);
        } catch (JwtException e) {
            throw new InvalidRefreshTokenException(e);
        }
        String use = jwt.getClaimAsString(TokenClaims.TOKEN_USE_CLAIM);
        if (!TokenClaims.REFRESH.equals(use)) throw new InvalidRefreshTokenException("리프레시 토큰이 아닙니다");
        // `Jwt.issuer` 는 iss 를 URL 로 바꾸다 "familyfitness" 같은 값에서 예외를 던지므로 문자열로 비교한다.
        if (!props.auth().jwt().issuer().equals(jwt.getClaimAsString(JwtClaimNames.ISS))) {
            throw new InvalidRefreshTokenException("발급자가 다릅니다");
        }
        Instant exp = jwt.getExpiresAt();
        if (exp == null) throw new InvalidRefreshTokenException("만료 시각이 없습니다");
        if (exp.isBefore(clock.instant())) throw new InvalidRefreshTokenException("만료된 리프레시 토큰입니다");
        return UUID.fromString(jwt.getSubject());
    }

    private String encode(UUID userId, Instant issuedAt, Instant expiresAt, String tokenUse) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.auth().jwt().issuer())
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE_CLAIM, tokenUse)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
