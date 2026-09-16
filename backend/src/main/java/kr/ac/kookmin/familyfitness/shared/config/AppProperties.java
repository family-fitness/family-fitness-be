package kr.ac.kookmin.familyfitness.shared.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("Asia/Seoul") String timezone,
        @DefaultValue("http://localhost:5173") String frontendBaseUrl,
        @DefaultValue Cors cors,
        @DefaultValue Auth auth,
        @DefaultValue Ai ai) {
    @ConstructorBinding
    public AppProperties {}

    public AppProperties() {
        this("Asia/Seoul", "http://localhost:5173", new Cors(), new Auth(), new Ai());
    }

    /** `*` 하나면 모든 출처를 허용한다(로컬). 운영은 프론트 도메인만 나열한다. */
    public record Cors(@DefaultValue List<String> allowedOrigins) {
        @ConstructorBinding
        public Cors {}

        public Cors() {
            this(List.of());
        }

        public boolean allowAll() {
            return allowedOrigins.stream().anyMatch(it -> it.trim().equals("*"));
        }
    }

    public record Auth(
            @DefaultValue Jwt jwt,
            @DefaultValue DevLogin devLogin,
            @DefaultValue DevAutoLogin devAutoLogin,
            @DefaultValue Google google) {
        @ConstructorBinding
        public Auth {}

        public Auth() {
            this(new Jwt(), new DevLogin(), new DevAutoLogin(), new Google());
        }
    }

    /** local 시연용. 토큰 없는 요청을 {@code userId} 로 인증한다. 운영에서는 항상 꺼져 있다. */
    public record DevAutoLogin(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String userId) {
        @ConstructorBinding
        public DevAutoLogin {}

        public DevAutoLogin() {
            this(false, "");
        }
    }

    public record Jwt(
            @DefaultValue("familyfitness") String issuer,
            /** HS256 대칭키. 32바이트 이상. 운영에서는 APP_JWT_SECRET 환경변수로 주입한다. */
            @DefaultValue("") String secret,
            @DefaultValue("1h") Duration accessTtl,
            @DefaultValue("30d") Duration refreshTtl) {
        @ConstructorBinding
        public Jwt {}

        public Jwt() {
            this("familyfitness", "", Duration.ofHours(1), Duration.ofDays(30));
        }
    }

    /** local 프로필 전용 개발 로그인. 운영 로그인 수단이 아니다. */
    public record DevLogin(@DefaultValue("false") boolean enabled) {
        @ConstructorBinding
        public DevLogin {}

        public DevLogin() {
            this(false);
        }
    }

    public record Google(
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret,

            @DefaultValue("https://oauth2.googleapis.com/token")
            String tokenUri,

            @DefaultValue("https://www.googleapis.com/oauth2/v3/certs")
            String jwkSetUri) {
        @ConstructorBinding
        public Google {}

        public Google() {
            this("", "", "https://oauth2.googleapis.com/token", "https://www.googleapis.com/oauth2/v3/certs");
        }
    }

    /** `stub`: AI 서비스 없이 결정적 가짜 응답 · `http`: FastAPI 내부망 호출 */
    public record Ai(
            @DefaultValue("stub") String mode,
            @DefaultValue("http://localhost:8000") String baseUrl) {
        @ConstructorBinding
        public Ai {}

        public Ai() {
            this("stub", "http://localhost:8000");
        }

        public boolean isStub() {
            return mode.equalsIgnoreCase("stub");
        }
    }
}
