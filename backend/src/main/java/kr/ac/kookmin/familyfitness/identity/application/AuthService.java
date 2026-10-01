package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider;
import kr.ac.kookmin.familyfitness.identity.application.port.RefreshTokenRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AccountNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.RefreshToken;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import kr.ac.kookmin.familyfitness.identity.domain.UserStatus;
import kr.ac.kookmin.familyfitness.shared.security.InvalidRefreshTokenException;
import kr.ac.kookmin.familyfitness.shared.security.RefreshTokenClaims;
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer;
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokens;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구글 로그인·개발용 로그인·리프레시·로그아웃·내 정보. 계정 병합 경로는 없다(provider 하나로 고정).
 *
 * <p>리프레시 토큰은 한 번만 쓴다(RFC 9700 §4.14.2 의 회전). 로그인할 때 새 묶음(familyId)을 열고, refresh 할 때마다
 * 같은 묶음에 새 토큰을 기록하고 옛 토큰을 폐기한다. 폐기된 토큰이 다시 오면 누가 훔쳐 쓴 것인지 가릴 수 없으므로 그 묶음을
 * 통째로 폐기한다. 액세스 토큰은 상태 없는 JWT 그대로라 폐기 뒤에도 만료(한 시간)까지는 통한다.
 *
 * <p>같은 토큰으로 두 요청이 동시에 와도 한쪽만 회전하는 것은 DB 격리 수준에 기댄다. refresh ⑤ 의 조건부 UPDATE
 * ({@code where jti = ? and revoked_at is null})가 그 행을 잠그고, 늦은 요청의 UPDATE 는 앞 트랜잭션이 끝날 때까지
 * 기다렸다가 WHERE 를 다시 평가해 0행을 받는다. 이것은 READ COMMITTED 의 동작이다(PostgreSQL · H2 의 기본값이고 이 저장소는
 * 바꾸지 않는다. PostgreSQL 문서 「Transaction Isolation」의 Read Committed 절). REPEATABLE READ 이상으로 올리면 늦은 요청은
 * 0행 대신 SQLSTATE 40001 오류를 받아 401 이 아닌 500 이 되고 묶음도 폐기되지 않는다. 격리 수준이나 ⑤ 의 조건을 바꾸면
 * H2ConcurrentRefreshTest · PostgresConcurrentRefreshTest 가 깨진다.
 */
@Service
@Transactional
public class AuthService {
    private final UserRegistrationService registration;
    private final GoogleIdentityProvider google;
    private final UserRepository users;
    private final FamilyRepository families;
    private final ProfileSummaries summaries;
    private final ServiceTokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokens;
    private final IdentityClock clock;
    private final TransactionTemplate tx;

    public AuthService(
            UserRegistrationService registration,
            GoogleIdentityProvider google,
            UserRepository users,
            FamilyRepository families,
            ProfileSummaries summaries,
            ServiceTokenIssuer tokenIssuer,
            RefreshTokenRepository refreshTokens,
            IdentityClock clock,
            TransactionTemplate tx) {
        this.registration = registration;
        this.google = google;
        this.users = users;
        this.families = families;
        this.summaries = summaries;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * 구글 토큰 교환(외부 HTTP · JWKS 조회)은 트랜잭션 밖에서 한다 — 트랜잭션 안이면 구글 응답을 기다리는 동안 DB 커넥션 하나를 쥔다
     * (QA SA-13). 교환이 끝난 뒤 가입(find-or-create)과 토큰 발급만 트랜잭션 하나로 묶는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthResult loginWithGoogle(String authorizationCode, String redirectUri, @Nullable String claimCode) {
        GoogleIdentity identity = google.exchange(authorizationCode, redirectUri);
        return Objects.requireNonNull(tx.execute(status -> {
            User user = registration.registerOrGet(User.PROVIDER_GOOGLE, identity.subject(), identity.email());
            return login(user.id(), claimCode);
        }));
    }

    /**
     * 개발용 로그인에 이 값을 보내면 부를 때마다 새 계정을 만든다(「demo-fresh-」 + 무작위 8자). FE 로그인 화면의 「새 계정 · 가족
     * 없음」 단추(fe:src/app/login/page.tsx)가 보내는 값이다 — 한 번 가족을 만들고 나면 같은 계정으로는 가족 만들기부터 다시 볼 수
     * 없어서, 메모리 DB 를 다시 띄우지 않고도 새 계정 흐름을 몇 번이고 보게 한다.
     */
    public static final String FRESH_DEV_USER = "demo-fresh";

    /**
     * local/compose/test 전용. 컨트롤러가 `app.auth.dev-login.enabled` 로만 열린다. 같은 providerUserId 면 같은 계정이다.
     * 딱 {@link #FRESH_DEV_USER} 일 때만 부를 때마다 새 계정이다.
     */
    public AuthResult devLogin(String providerUserId, @Nullable String email, @Nullable String claimCode) {
        String subject = FRESH_DEV_USER.equals(providerUserId)
                ? FRESH_DEV_USER + "-" + UUID.randomUUID().toString().substring(0, 8)
                : providerUserId;
        User user = registration.registerOrGet(User.PROVIDER_DEV, subject, email);
        return login(user.id(), claimCode);
    }

    /**
     * 판단 순서: ① 서명 · 용도 · 발급자 · 만료 ② 발급 기록이 있고 계정이 같은가 ③ 이미 폐기됐는가(재사용이면 묶음 폐기)
     * ④ 계정이 ACTIVE 인가(아니면 묶음 폐기) ⑤ 옛 토큰을 조건부로 폐기(동시에 온 두 번째 요청은 여기서 재사용으로 걸린다)
     * ⑥ 새 토큰을 같은 묶음에 기록. 폐기는 401 을 던지면서도 남아야 하므로 이 예외로는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult refresh(String refreshToken) {
        RefreshTokenClaims claims = tokenIssuer.readRefreshToken(refreshToken);
        RefreshToken stored = refreshTokens.findById(claims.tokenId());
        if (stored == null || !stored.userId().equals(claims.userId())) {
            throw new InvalidRefreshTokenException("발급 기록이 없는 리프레시 토큰입니다");
        }
        Instant now = clock.now();
        if (stored.revoked()) throw reuseDetected(stored, now);
        User user = users.findById(stored.userId());
        if (user == null || user.status() != UserStatus.ACTIVE) {
            refreshTokens.revokeFamily(stored.familyId(), now);
            throw new InvalidRefreshTokenException("쓸 수 없는 계정입니다");
        }
        ServiceTokens tokens = tokenIssuer.issue(user.id());
        if (!refreshTokens.rotate(stored.id(), tokens.refreshTokenId(), now)) throw reuseDetected(stored, now);
        refreshTokens.add(RefreshToken.issued(
                tokens.refreshTokenId(), user.id(), stored.familyId(), now, tokens.refreshExpiresAt()));
        return new AuthResult(tokens, session(user.id(), null));
    }

    /**
     * 그 리프레시 토큰이 속한 묶음을 폐기한다. 토큰이 없거나 · 검증에 실패하거나 · 기록이 없으면 아무것도 하지 않는다(멱등).
     * 액세스 토큰은 건드리지 않는다.
     */
    public void logout(@Nullable String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        RefreshTokenClaims claims;
        try {
            claims = tokenIssuer.readRefreshToken(refreshToken);
        } catch (InvalidRefreshTokenException e) {
            return;
        }
        RefreshToken stored = refreshTokens.findById(claims.tokenId());
        if (stored == null || !stored.userId().equals(claims.userId())) return;
        refreshTokens.revokeFamily(stored.familyId(), clock.now());
    }

    @Transactional(readOnly = true)
    public AuthSession session(UUID userId) {
        return session(userId, null);
    }

    /**
     * GET /me. 계정이 없으면(탈퇴함) 401 UNAUTHORIZED 다. 액세스 토큰은 탈퇴 뒤에도 만료까지 서명 검사를 지나므로 여기서 계정을 찾아
     * 본다. 없는 계정을 가족 없는 계정(CREATE_FAMILY)으로 보여 주면 화면이 지운 계정으로 가족 만들기를 시작한다.
     */
    @Transactional(readOnly = true)
    public AuthSession me(UUID userId) {
        if (users.findById(userId) == null) throw new AccountNotFoundException();
        return session(userId, null);
    }

    @Transactional(readOnly = true)
    public AuthSession session(UUID userId, @Nullable String claimCode) {
        List<ProfileSummary> profiles =
                families.profilesOfUser(userId).stream().map(summaries::summary).toList();
        return AuthSession.of(userId, NextStep.afterLogin(profiles, claimCode), profiles);
    }

    /**
     * 이미 확인한 계정으로 로그인시킨다 — 심사용 계정 로그인({@link ReviewLoginService})이 계정과 가족을 만든 뒤 부른다. 초대코드를 주면
     * 가족이 없는 계정은 nextStep CLAIM 이다(개발용 로그인에 claimCode 를 실어 보낸 것과 같다).
     */
    AuthResult startSession(UUID userId, @Nullable String claimCode) {
        return login(userId, claimCode);
    }

    /** 로그인마다 새 묶음을 연다. 기기(브라우저)마다 묶음이 따로라 한 기기의 로그아웃 · 재사용 감지가 다른 기기를 끊지 않는다. */
    private AuthResult login(UUID userId, @Nullable String claimCode) {
        ServiceTokens tokens = tokenIssuer.issue(userId);
        refreshTokens.add(RefreshToken.issued(
                tokens.refreshTokenId(), userId, UUID.randomUUID(), clock.now(), tokens.refreshExpiresAt()));
        return new AuthResult(tokens, session(userId, claimCode));
    }

    private InvalidRefreshTokenException reuseDetected(RefreshToken stored, Instant now) {
        refreshTokens.revokeFamily(stored.familyId(), now);
        return new InvalidRefreshTokenException("이미 쓴 리프레시 토큰입니다");
    }
}
