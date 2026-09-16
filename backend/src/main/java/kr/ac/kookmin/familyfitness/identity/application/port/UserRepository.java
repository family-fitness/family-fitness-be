package kr.ac.kookmin.familyfitness.identity.application.port;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.jspecify.annotations.Nullable;

public interface UserRepository {
    @Nullable
    User findById(UUID id);

    @Nullable
    User findByProviderAndProviderUserId(String provider, String providerUserId);

    User save(User user);
}
