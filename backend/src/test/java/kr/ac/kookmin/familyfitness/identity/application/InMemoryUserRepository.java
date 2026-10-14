package kr.ac.kookmin.familyfitness.identity.application;

import java.util.LinkedHashMap;
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

    @Override
    public void recordSignupSource(UUID userId, SignupSource source) {
        if (source.isEmpty() || !users.containsKey(userId)) return;
        signupSources.putIfAbsent(userId, source);
    }
}
