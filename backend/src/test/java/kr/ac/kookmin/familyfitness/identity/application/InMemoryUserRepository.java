package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.SignupSource;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.jspecify.annotations.Nullable;

class InMemoryUserRepository implements UserRepository {
    final Map<UUID, User> users = new LinkedHashMap<>();

    /** 계정마다 처음 남긴 유입 경로(V174). */
    final Map<UUID, SignupSource> signupSources = new LinkedHashMap<>();

    @Override
    public @Nullable User findById(UUID id) {
        return users.get(id);
    }

    @Override
    public @Nullable User findByProviderAndProviderUserId(String provider, String providerUserId) {
        return users.values().stream()
                .filter(it ->
                        it.provider().equals(provider) && it.providerUserId().equals(providerUserId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public User save(User user) {
        users.put(user.id(), user);
        return user;
    }

    /** 만든 시각을 두지 않으므로 그 제공자의 계정을 넣은 차례대로 준다. */
    @Override
    public List<UUID> findIdsByProviderCreatedBefore(String provider, Instant before, int limit) {
        return users.values().stream()
                .filter(it -> it.provider().equals(provider))
                .map(User::id)
                .limit(limit)
                .toList();
    }

    @Override
    public void recordSignupSource(UUID userId, SignupSource source) {
        if (source.isEmpty() || !users.containsKey(userId)) return;
        signupSources.putIfAbsent(userId, source);
    }
}
