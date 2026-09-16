package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleAuthFailedException;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.security.InvalidRefreshTokenException;
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthServiceTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-08T10:00:00Z"));
    private final IdentityClock identityClock = clock.identityClock();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryFamilyRepository families = new InMemoryFamilyRepository();
    private final ProfileSummaries summaries = new ProfileSummaries(identityClock);
    private final AppProperties props = new AppProperties(
            "Asia/Seoul",
            "http://localhost:5173",
            new AppProperties.Cors(),
            new AppProperties.Auth(
                    new AppProperties.Jwt(
                            "familyfitness",
                            "test-only-secret-test-only-secret-0123456789",
                            Duration.ofHours(1),
                            Duration.ofDays(30)),
                    new AppProperties.DevLogin(),
                    new AppProperties.DevAutoLogin(),
                    new AppProperties.Google()),
            new AppProperties.Ai());
    private final SecretKeySpec key =
            new SecretKeySpec(props.auth().jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final ServiceTokenIssuer tokenIssuer =
            new ServiceTokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)), key, props, clock);

    private static final class FakeGoogle implements GoogleIdentityProvider {
        @Nullable
        GoogleIdentity next = new GoogleIdentity("google-sub-1", "parent@example.com");

        @Override
        public GoogleIdentity exchange(String authorizationCode, String redirectUri) {
            GoogleIdentity identity = next;
            if (identity == null) throw new GoogleAuthFailedException();
            return identity;
        }
    }

    private final FakeGoogle google = new FakeGoogle();

    private final AuthService service =
            new AuthService(new UserRegistrationService(users), google, users, families, summaries, tokenIssuer);
    private final FamilyService familyService = new FamilyService(families, summaries, identityClock);

    @Test
    @DisplayName("구글 첫 로그인은 계정을 만들고 프로필이 없으면 CREATE_FAMILY 로 보낸다")
    void 구글_첫_로그인은_계정을_만들고_프로필이_없으면_CREATE_FAMILY_로_보낸다() {
        AuthResult result = service.loginWithGoogle("code", "https://app/redirect", null);

        assertThat(result.tokens().accessToken()).isNotBlank();
        assertThat(result.tokens().refreshToken()).isNotBlank();
        assertThat(result.session().profiles()).isEmpty();
        assertThat(result.session().nextStep()).isEqualTo(NextStep.CREATE_FAMILY);
        assertThat(users.users.values()).hasSize(1);
        User user = users.users.values().iterator().next();
        assertThat(user.provider()).isEqualTo(User.PROVIDER_GOOGLE);
        assertThat(user.providerUserId()).isEqualTo("google-sub-1");
        assertThat(user.email()).isEqualTo("parent@example.com");
        assertThat(result.session().userId()).isEqualTo(user.id());
    }

    @Test
    @DisplayName("초대 코드를 들고 온 새 계정은 CLAIM 으로, 프로필이 있으면 HOME 으로 보낸다")
    void 초대_코드를_들고_온_새_계정은_CLAIM_으로_프로필이_있으면_HOME_으로_보낸다() {
        AuthResult withCode = service.loginWithGoogle("code", "https://app/redirect", "ABC234");
        assertThat(withCode.session().nextStep()).isEqualTo(NextStep.CLAIM);

        familyService.createFamily(withCode.session().userId(), "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F);

        AuthResult again = service.loginWithGoogle("code", "https://app/redirect", "ABC234");
        assertThat(again.session().userId()).isEqualTo(withCode.session().userId());
        assertThat(again.session().nextStep()).isEqualTo(NextStep.HOME);
        assertThat(again.session().profiles()).hasSize(1);
        assertThat(users.users).hasSize(1);
    }

    @Test
    @DisplayName("구글 인증 실패는 그대로 올라간다")
    void 구글_인증_실패는_그대로_올라간다() {
        google.next = null;

        assertThrows(
                GoogleAuthFailedException.class, () -> service.loginWithGoogle("bad", "https://app/redirect", null));
        assertThat(users.users).isEmpty();
    }

    @Test
    @DisplayName("개발용 로그인은 DEV 제공자로 계정을 만든다")
    void 개발용_로그인은_DEV_제공자로_계정을_만든다() {
        AuthResult result = service.devLogin("dev-parent", null, null);

        assertThat(users.users.values()).hasSize(1);
        User user = users.users.values().iterator().next();
        assertThat(user.provider()).isEqualTo(User.PROVIDER_DEV);
        assertThat(user.providerUserId()).isEqualTo("dev-parent");
        assertThat(result.session().nextStep()).isEqualTo(NextStep.CREATE_FAMILY);
        assertThat(service.devLogin("dev-parent", "x@example.com", null)
                        .session()
                        .userId())
                .isEqualTo(user.id());
    }

    @Test
    @DisplayName("리프레시는 리프레시 토큰만 받고 계정이 있어야 한다")
    void 리프레시는_리프레시_토큰만_받고_계정이_있어야_한다() {
        AuthResult login = service.devLogin("dev-parent", null, null);

        AuthResult refreshed = service.refresh(login.tokens().refreshToken());
        assertThat(refreshed.session().userId()).isEqualTo(login.session().userId());
        assertThat(refreshed.session().nextStep()).isEqualTo(NextStep.CREATE_FAMILY);

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.refresh(login.tokens().accessToken()));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("not-a-token"));

        String orphan = tokenIssuer.issue(UUID.randomUUID()).refreshToken();
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(orphan));
    }

    @Test
    @DisplayName("내 정보는 계정과 프로필 목록을 준다")
    void 내_정보는_계정과_프로필_목록을_준다() {
        AuthResult login = service.devLogin("dev-parent", null, null);
        familyService.createFamily(login.session().userId(), "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F);

        AuthSession session = service.session(login.session().userId());

        assertThat(session.nextStep()).isEqualTo(NextStep.HOME);
        assertThat(session.profiles()).singleElement().extracting("name").isEqualTo("엄마");
    }
}
