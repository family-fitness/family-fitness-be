package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.AccountDeletionService.ReviewErasure;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정 정리. 공모전 심사용 로그인(POST /auth/review-login)이 만든 계정과 체험 가족을 만든 지 보관 기간(기본 31일)이 지나면 지운다.
 * <ul>
 *   <li>보관 기간을 리프레시 토큰 수명(30일)보다 길게 둔다. 심사용 계정은 같은 계정으로 다시 로그인하는 길이 없어서, 토큰이 끝난 뒤의
 *       계정과 기록은 아무도 볼 수 없는 데이터다.
 *   <li>매일 한 번(기본 04:30, app.timezone 기준) 돈다. 한 번에 최대 {@link #BATCH} 계정까지 보고, 계정마다 따로 지운다(트랜잭션 하나씩).
 *       한 계정이 실패해도 나머지는 계속한다.
 *   <li>지우는 규칙(가족째로 지움, 실제 사용자가 낀 가족은 남김)은 {@link AccountDeletionService#eraseReviewAccount} 에 있다.
 * </ul>
 * 이용 지표 조회문(deploy/sql/usage-metrics.sql)은 원래 심사용 계정을 빼고 세므로, 지워도 지표 값은 바뀌지 않는다.
 */
@Component
public class ReviewAccountSweeper {
    /** 한 번에 보는 계정 수. 남은 계정은 다음 날 이어서 지운다. */
    static final int BATCH = 500;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final UserRepository users;
    private final AccountDeletionService deletion;
    private final IdentityClock clock;
    private final Duration retention;

    public ReviewAccountSweeper(
            UserRepository users,
            AccountDeletionService deletion,
            IdentityClock clock,
            @Value("${app.auth.review-cleanup.retention:P31D}") Duration retention) {
        if (retention.compareTo(Duration.ofDays(30)) <= 0) {
            throw new IllegalStateException("app.auth.review-cleanup.retention(" + retention
                    + ")은 30일(리프레시 토큰 수명)보다 길어야 합니다. 더 짧으면 아직 쓰는 심사용 계정이 지워질 수 있습니다");
        }
        this.users = users;
        this.deletion = deletion;
        this.clock = clock;
        this.retention = retention;
    }

    /** 매일 한 번. 만든 지 보관 기간이 지난 심사용 계정을 지운다. */
    @Scheduled(cron = "${app.auth.review-cleanup.cron:0 30 4 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public Run sweep() {
        return sweepCreatedBefore(clock.now().minus(retention));
    }

    /** {@code before} 보다 먼저 만든 심사용 계정을 지운다. */
    Run sweepCreatedBefore(Instant before) {
        List<UUID> candidates = users.findIdsByProviderCreatedBefore(User.PROVIDER_REVIEW, before, BATCH);
        int erased = 0;
        int kept = 0;
        int failed = 0;
        for (UUID userId : candidates) {
            try {
                ReviewErasure result = deletion.eraseReviewAccount(userId);
                if (result == ReviewErasure.ERASED) erased++;
                if (result == ReviewErasure.KEPT_REAL_MEMBER) kept++;
            } catch (RuntimeException e) {
                failed++;
                log.error("심사용 계정을 지우지 못했다: {}", userId, e);
            }
        }
        if (!candidates.isEmpty()) {
            log.info(
                    "심사용 계정 정리: {} 전에 만든 계정 {}개 중 {}개 지움, 실제 사용자가 낀 가족이라 남김 {}개, 실패 {}개",
                    before,
                    candidates.size(),
                    erased,
                    kept,
                    failed);
        }
        return new Run(candidates.size(), erased, kept, failed);
    }

    /**
     * 한 번 돈 결과.
     *
     * @param candidates 본 계정 수
     * @param erased 지운 계정 수(같은 가족의 다른 심사용 계정은 세지 않는다. 그 계정은 다음 차례에서 NOT_REVIEW 로 넘어간다)
     * @param kept 실제 사용자가 낀 가족이라 남긴 계정 수
     * @param failed 실패한 계정 수
     */
    public record Run(int candidates, int erased, int kept, int failed) {}
}
