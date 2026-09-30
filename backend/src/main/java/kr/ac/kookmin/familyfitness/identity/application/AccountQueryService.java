package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.AccountQuery;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService implements AccountQuery {
    private final UserRepository users;

    public AccountQueryService(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean isReviewAccount(UUID userId) {
        User user = users.findById(userId);
        return user != null && User.PROVIDER_REVIEW.equals(user.provider());
    }
}
