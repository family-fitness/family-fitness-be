package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    @Nullable
    UserEntity findByProviderAndProviderUserId(String provider, String providerUserId);
}
