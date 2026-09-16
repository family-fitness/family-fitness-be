package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import kr.ac.kookmin.familyfitness.identity.domain.UserStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository jpa;
    private final EntityManager em;
    private final Clock clock;

    public UserRepositoryAdapter(UserJpaRepository jpa, EntityManager em, Clock clock) {
        this.jpa = jpa;
        this.em = em;
        this.clock = clock;
    }

    @Override
    public @Nullable User findById(UUID id) {
        return jpa.findById(id).map(UserRepositoryAdapter::toDomain).orElse(null);
    }

    @Override
    public @Nullable User findByProviderAndProviderUserId(String provider, String providerUserId) {
        UserEntity entity = jpa.findByProviderAndProviderUserId(provider, providerUserId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public User save(User user) {
        Instant now = clock.instant();
        UserEntity existing = jpa.findById(user.id()).orElse(null);
        if (existing == null) {
            em.persist(new UserEntity(
                    user.id(),
                    user.provider(),
                    user.providerUserId(),
                    user.email(),
                    user.status().name(),
                    now,
                    now));
        } else {
            existing.setEmail(user.email());
            existing.setStatus(user.status().name());
            existing.setUpdatedAt(now);
        }
        return user;
    }

    private static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getProvider(),
                entity.getProviderUserId(),
                entity.getEmail(),
                UserStatus.valueOf(entity.getStatus()));
    }
}
