package kr.ac.kookmin.familyfitness.identity.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.jspecify.annotations.Nullable;

class InMemoryUserRepository implements UserRepository {
    final Map<UUID, User> users = new LinkedHashMap<>();

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
}
