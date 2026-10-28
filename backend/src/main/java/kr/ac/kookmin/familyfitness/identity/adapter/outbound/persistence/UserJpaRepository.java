package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    @Nullable
    UserEntity findByProviderAndProviderUserId(String provider, String providerUserId);

    @Query("select u.id from UserEntity u where u.provider = :provider and u.createdAt < :before order by u.createdAt")
    List<UUID> findIdsByProviderCreatedBefore(String provider, Instant before, Limit limit);
}
