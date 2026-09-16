package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentity;
import kr.ac.kookmin.familyfitness.identity.application.port.GoogleIdentityProvider;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import kr.ac.kookmin.familyfitness.shared.security.InvalidRefreshTokenException;
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구글 로그인·개발용 로그인·리프레시·내 정보. 계정 병합 경로는 없다(provider 하나로 고정). */
@Service
@Transactional
public class AuthService {
    private final UserRegistrationService registration;
    private final GoogleIdentityProvider google;
    private final UserRepository users;
    private final FamilyRepository families;
    private final ProfileSummaries summaries;
    private final ServiceTokenIssuer tokenIssuer;

    public AuthService(
            UserRegistrationService registration,
            GoogleIdentityProvider google,
            UserRepository users,
            FamilyRepository families,
            ProfileSummaries summaries,
            ServiceTokenIssuer tokenIssuer) {
        this.registration = registration;
        this.google = google;
        this.users = users;
        this.families = families;
        this.summaries = summaries;
        this.tokenIssuer = tokenIssuer;
    }

    public AuthResult loginWithGoogle(String authorizationCode, String redirectUri, @Nullable String claimCode) {
        GoogleIdentity identity = google.exchange(authorizationCode, redirectUri);
        User user = registration.registerOrGet(User.PROVIDER_GOOGLE, identity.subject(), identity.email());
        return issue(user.id(), claimCode);
    }

    /** local/compose/test 전용. 컨트롤러가 `app.auth.dev-login.enabled` 로만 열린다. */
    public AuthResult devLogin(String providerUserId, @Nullable String email, @Nullable String claimCode) {
        User user = registration.registerOrGet(User.PROVIDER_DEV, providerUserId, email);
        return issue(user.id(), claimCode);
    }

    public AuthResult refresh(String refreshToken) {
        UUID userId = tokenIssuer.userIdOfRefreshToken(refreshToken);
        if (users.findById(userId) == null) throw new InvalidRefreshTokenException("계정이 없습니다");
        return issue(userId, null);
    }

    @Transactional(readOnly = true)
    public AuthSession session(UUID userId) {
        return session(userId, null);
    }

    @Transactional(readOnly = true)
    public AuthSession session(UUID userId, @Nullable String claimCode) {
        List<ProfileSummary> profiles =
                families.profilesOfUser(userId).stream().map(summaries::summary).toList();
        return new AuthSession(userId, NextStep.afterLogin(profiles, claimCode), profiles);
    }

    private AuthResult issue(UUID userId, @Nullable String claimCode) {
        return new AuthResult(tokenIssuer.issue(userId), session(userId, claimCode));
    }
}
