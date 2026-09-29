package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleAuthFailedException;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider;
import kr.ac.kookmin.familyfitness.identity.domain.RefreshToken;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import kr.ac.kookmin.familyfitness.identity.domain.UserStatus;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.security.InvalidRefreshTokenException;
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
                    new AppProperties.ReviewLogin(),
                    new AppProperties.Google()),
            new AppProperties.Ai());
    private final SecretKeySpec key =
            new SecretKeySpec(props.auth().jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final ServiceTokenIssuer tokenIssuer =
            new ServiceTokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)), key, props, clock);

    private static final class FakeGoogle implements GoogleIdentityProvider {
        @Nullable
        GoogleIdentity next = new GoogleIdentity("google-sub-1", "parent@example.com");

        /** 토큰 교환을 부른 순간 트랜잭션이 열려 있었나. 부르지 않았으면 null */
        @Nullable
        Boolean transactionDuringExchange;

        @Override
        public GoogleIdentity exchange(String authorizationCode, String redirectUri) {
            transactionDuringExchange = TransactionSynchronizationManager.isActualTransactionActive();
            GoogleIdentity identity = next;
            if (identity == null) throw new GoogleAuthFailedException();
            return identity;
        }
    }

    private final FakeGoogle google = new FakeGoogle();
    private final InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();

    private final AuthService service = new AuthService(
            new UserRegistrationService(users),
            google,
            users,
            families,
            summaries,
            tokenIssuer,
            refreshTokens,
            identityClock,
            new TransactionTemplate(new CountingTransactionManager()));
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
    @DisplayName("구글 토큰 교환(외부 HTTP)은 트랜잭션 밖에서 하고, 가입 · 토큰 발급만 트랜잭션 하나로 묶는다")
    void 구글_토큰_교환은_트랜잭션_밖에서_한다() {
        CountingTransactionManager transactions = new CountingTransactionManager();
        List<Boolean> transactionDuringRegistration = new ArrayList<>();
        UserRegistrationService registration = new UserRegistrationService(users) {
            @Override
            public User registerOrGet(String provider, String providerUserId, @Nullable String email) {
                transactionDuringRegistration.add(TransactionSynchronizationManager.isActualTransactionActive());
                return super.registerOrGet(provider, providerUserId, email);
            }
        };
        AuthService transactional = withTransactionAdvice(
                new AuthService(
                        registration,
                        google,
                        users,
                        families,
                        summaries,
                        tokenIssuer,
                        refreshTokens,
                        identityClock,
                        new TransactionTemplate(transactions)),
                transactions);

        AuthResult result = transactional.loginWithGoogle("code", "https://app/redirect", null);

        assertThat(google.transactionDuringExchange).isFalse();
        assertThat(transactionDuringRegistration).containsExactly(true);
        assertThat(transactions.begun).isOne();
        assertThat(refreshTokens.findById(tokenIssuer
                        .readRefreshToken(result.tokens().refreshToken())
                        .tokenId()))
                .isNotNull();
    }

    /** 스프링이 빈에 거는 것과 같은 @Transactional 해석기(TransactionInterceptor)로 감싼다. */
    private static AuthService withTransactionAdvice(AuthService target, PlatformTransactionManager transactions) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(
                (TransactionManager) transactions, new AnnotationTransactionAttributeSource()));
        return (AuthService) factory.getProxy();
    }

    /** 자원 없는 트랜잭션 관리자. 새로 시작한 트랜잭션 수만 센다. */
    private static final class CountingTransactionManager extends AbstractPlatformTransactionManager {
        int begun;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begun++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
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
    @DisplayName("개발용 로그인에 demo-fresh 를 보내면 부를 때마다 가족 없는 새 계정이다 — FE 「새 계정」 단추를 몇 번이고 누를 수 있게")
    void 개발용_로그인_demo_fresh_는_부를_때마다_새_계정이다() {
        AuthResult first = service.devLogin(AuthService.FRESH_DEV_USER, null, null);
        AuthResult second = service.devLogin(AuthService.FRESH_DEV_USER, null, null);

        assertThat(AuthService.FRESH_DEV_USER).isEqualTo("demo-fresh");
        assertThat(second.session().userId()).isNotEqualTo(first.session().userId());
        assertThat(List.of(first, second))
                .allSatisfy(it -> assertThat(it.session().nextStep()).isEqualTo(NextStep.CREATE_FAMILY));
        assertThat(users.users.values()).hasSize(2).allSatisfy(it -> {
            assertThat(it.provider()).isEqualTo(User.PROVIDER_DEV);
            assertThat(it.providerUserId()).startsWith("demo-fresh-");
        });

        // 딱 그 값일 때만이다 — 비슷한 값은 여느 개발용 계정처럼 같은 계정으로 돌아온다
        UUID similar = service.devLogin("demo-fresh-x", null, null).session().userId();
        assertThat(service.devLogin("demo-fresh-x", null, null).session().userId())
                .isEqualTo(similar);
        assertThat(users.users).hasSize(3);
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
        // 서명은 맞지만 발급 기록이 없는 토큰(이 표가 생기기 전에 받은 토큰 등)도 거부한다.
        String unrecorded = tokenIssuer.issue(login.session().userId()).refreshToken();
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(unrecorded));
    }

    private UUID jti(String refreshToken) {
        return tokenIssuer.readRefreshToken(refreshToken).tokenId();
    }

    @Test
    @DisplayName("리프레시는 새 리프레시 토큰을 주고, 받은 토큰은 폐기하며 다음 토큰을 같은 묶음에 기록한다")
    void 리프레시는_새_리프레시_토큰을_주고_받은_토큰은_폐기하며_다음_토큰을_같은_묶음에_기록한다() {
        AuthResult login = service.devLogin("dev-parent", null, null);
        String first = login.tokens().refreshToken();
        assertThat(refreshTokens.tokens).containsOnlyKeys(jti(first));

        String second = service.refresh(first).tokens().refreshToken();

        assertThat(second).isNotEqualTo(first);
        RefreshToken old = refreshTokens.tokens.get(jti(first));
        RefreshToken next = refreshTokens.tokens.get(jti(second));
        assertThat(old.revokedAt()).isEqualTo(clock.instant());
        assertThat(old.replacedBy()).isEqualTo(jti(second));
        assertThat(next.familyId()).isEqualTo(old.familyId());
        assertThat(next.userId()).isEqualTo(login.session().userId());
        assertThat(next.revoked()).isFalse();
        assertThat(next.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        assertThat(service.refresh(second).session().userId())
                .isEqualTo(login.session().userId());
    }

    @Test
    @DisplayName("폐기된 리프레시 토큰이 다시 오면 그 묶음을 모두 폐기하고, 다른 로그인의 묶음은 그대로 둔다")
    void 폐기된_리프레시_토큰이_다시_오면_그_묶음을_모두_폐기하고_다른_로그인의_묶음은_그대로_둔다() {
        AuthResult phone = service.devLogin("dev-parent", null, null);
        AuthResult tablet = service.devLogin("dev-parent", null, null);
        String stolen = phone.tokens().refreshToken();
        String current = service.refresh(stolen).tokens().refreshToken();

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(stolen));

        assertThat(refreshTokens.tokens.get(jti(current)).revoked()).isTrue();
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(current));
        assertThat(service.refresh(tablet.tokens().refreshToken()).session().userId())
                .isEqualTo(tablet.session().userId());
    }

    @Test
    @DisplayName("같은 토큰으로 온 두 요청 중 회전에 늦은 쪽은 재사용으로 보고 그 묶음을 폐기한다")
    void 같은_토큰으로_온_두_요청_중_회전에_늦은_쪽은_재사용으로_보고_그_묶음을_폐기한다() {
        String token = service.devLogin("dev-parent", null, null).tokens().refreshToken();
        String[] winner = new String[1];
        // 이 요청이 기록을 읽은 뒤 · 옛 토큰을 폐기하기 전에 다른 요청이 같은 토큰으로 회전을 끝낸다.
        refreshTokens.beforeRotate =
                () -> winner[0] = service.refresh(token).tokens().refreshToken();

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(token));

        assertThat(winner[0]).isNotNull();
        assertThat(refreshTokens.tokens.get(jti(winner[0])).revoked()).isTrue();
        assertThat(refreshTokens.tokens).hasSize(2);
    }

    @Test
    @DisplayName("로그아웃은 그 묶음만 폐기하고, 토큰이 없거나 모르는 토큰이면 아무것도 하지 않는다")
    void 로그아웃은_그_묶음만_폐기하고_토큰이_없거나_모르는_토큰이면_아무것도_하지_않는다() {
        AuthResult phone = service.devLogin("dev-parent", null, null);
        AuthResult tablet = service.devLogin("dev-parent", null, null);
        String rotated = service.refresh(phone.tokens().refreshToken()).tokens().refreshToken();

        service.logout(rotated);

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(rotated));
        service.logout(rotated);
        service.logout(null);
        service.logout(" ");
        service.logout("not-a-token");
        service.logout(phone.tokens().accessToken());
        service.logout(tokenIssuer.issue(phone.session().userId()).refreshToken());
        assertThat(refreshTokens.tokens.get(jti(tablet.tokens().refreshToken())).revoked())
                .isFalse();
        assertThat(service.refresh(tablet.tokens().refreshToken()).session().userId())
                .isEqualTo(tablet.session().userId());
    }

    @Test
    @DisplayName("쓸 수 없는 계정(INACTIVE)은 리프레시하지 못하고 그 묶음도 폐기된다")
    void 쓸_수_없는_계정은_리프레시하지_못하고_그_묶음도_폐기된다() {
        AuthResult login = service.devLogin("dev-parent", null, null);
        User user = users.users.get(login.session().userId());
        users.save(new User(user.id(), user.provider(), user.providerUserId(), user.email(), UserStatus.INACTIVE));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.refresh(login.tokens().refreshToken()));

        assertThat(refreshTokens.tokens.get(jti(login.tokens().refreshToken())).revoked())
                .isTrue();
    }

    @Test
    @DisplayName("만료된 리프레시 토큰은 기록이 살아 있어도 거부한다")
    void 만료된_리프레시_토큰은_기록이_살아_있어도_거부한다() {
        String token = service.devLogin("dev-parent", null, null).tokens().refreshToken();

        clock.setInstant(clock.instant().plus(Duration.ofDays(30)).plusSeconds(1));

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(token));
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
