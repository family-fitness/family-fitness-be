package kr.ac.kookmin.familyfitness.identity.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.SignupSource;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.jspecify.annotations.Nullable;

public interface UserRepository {
    @Nullable
    User findById(UUID id);

    @Nullable
    User findByProviderAndProviderUserId(String provider, String providerUserId);

    User save(User user);

    /** 이 제공자의 계정 가운데 {@code before} 보다 먼저 만든 계정의 id. 오래된 것부터 최대 {@code limit} 개. */
    List<UUID> findIdsByProviderCreatedBefore(String provider, Instant before, int limit);

    /**
     * 처음 가입한 계정의 유입 경로(V174)를 남긴다. 이미 값이 있는 계정이나 없는 계정이면 아무것도 하지 않는다 — 첫 유입 경로가 남는다.
     */
    void recordSignupSource(UUID userId, SignupSource source);
}
