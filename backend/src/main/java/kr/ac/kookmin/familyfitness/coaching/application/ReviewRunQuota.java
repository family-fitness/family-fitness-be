package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.ReviewRunLimitException;
import kr.ac.kookmin.familyfitness.identity.api.AccountQuery;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정의 편성 횟수를 하루(KST)마다 센다. 심사용 계정은 구글 계정 없이 누구나 만들 수 있고, 편성마다 AI 가 LLM 을 부른다.
 * 편성에는 (대상, 날짜)마다 RUNNING 하나라는 잠금만 있어, 한도가 없으면 한 계정이 편성을 몇 번이든 돌릴 수 있다.
 * 한 계정이 하루에 {@link #MAX_RUNS_PER_DAY} 번을 넘기면 429 TOO_MANY 다. 구글 계정은 세지 않는다.
 *
 * <p>통과한 요청만 센다. 셈은 이 프로세스의 메모리다(서버 한 대 기준, 재시작하면 빈다) — ReviewLoginLimiter 와 같다.
 */
@Component
public class ReviewRunQuota {
    static final int MAX_RUNS_PER_DAY = 20;

    private final AccountQuery accounts;
    private final AppTime time;
    private final Map<UUID, Count> counts = new HashMap<>();

    private record Count(LocalDate day, int runs) {}

    public ReviewRunQuota(AccountQuery accounts, AppTime time) {
        this.accounts = accounts;
        this.time = time;
    }

    /** 심사용 계정이면 오늘 셈에 하나를 더한다. 이미 한도면 세지 않고 {@link ReviewRunLimitException}. */
    public void acquire(UUID userId) {
        if (!accounts.isReviewAccount(userId)) return;
        LocalDate today = time.today();
        synchronized (counts) {
            counts.values().removeIf(it -> !it.day().equals(today));
            Count count = counts.getOrDefault(userId, new Count(today, 0));
            if (count.runs() >= MAX_RUNS_PER_DAY) throw new ReviewRunLimitException(MAX_RUNS_PER_DAY);
            counts.put(userId, new Count(today, count.runs() + 1));
        }
    }
}
