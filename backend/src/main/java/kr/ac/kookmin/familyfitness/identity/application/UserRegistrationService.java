package kr.ac.kookmin.familyfitness.identity.application;

import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.SignupSource;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 인증을 마친 사용자의 최초 가입 또는 기존 계정 조회(find-or-create).
 * provider·providerUserId 는 서버가 검증한 인증 결과에서만 온다.
 */
@Service
@Transactional
public class UserRegistrationService {
    private final UserRepository repository;

    public UserRegistrationService(UserRepository repository) {
        this.repository = repository;
    }

    public User registerOrGet(String provider, String providerUserId, @Nullable String email) {
        return registerOrGet(provider, providerUserId, email, SignupSource.NONE);
    }

    /**
     * source 는 이번에 처음 가입하는 계정일 때만 남긴다. 이미 있는 계정으로 다시 로그인하면 버린다 — 첫 유입 경로가 남는다.
     */
    public User registerOrGet(String provider, String providerUserId, @Nullable String email, SignupSource source) {
        User existing = repository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existing != null) return existing;
        User user = User.register(provider, providerUserId, email);
        repository.save(user);
        if (!source.isEmpty()) repository.recordSignupSource(user.id(), source);
        return user;
    }
}
