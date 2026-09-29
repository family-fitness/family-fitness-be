package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.ReviewRunLimitException;
import kr.ac.kookmin.familyfitness.identity.api.AccountQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정의 편성 횟수를 하루(KST)마다 센다. 심사용 계정은 구글 계정 없이 누구나 만들 수 있고, 편성마다 AI 가 LLM 을 부른다.
 * 편성에는 (대상, 날짜)마다 RUNNING 하나라는 잠금만 있어, 한도가 없으면 한 계정이 편성을 몇 번이든 돌릴 수 있다. 한도는 둘이다.
 * <ul>
 *   <li>한 계정이 하루에 {@link #MAX_RUNS_PER_DAY} 번을 넘기면 429 TOO_MANY 다.
 *   <li>심사용 계정을 모두 합쳐 하루에 {@link #MAX_AI_RUNS_PER_DAY} 번 AI 로 짰으면, 그날 남은 심사용 편성은 AI 를 부르지 않고
 *       라벨 대체 편성({@link LabelBasedProposalPlanner})으로 짠다({@link Planner#LABELS}). 계정은 한 IP 에서 한 시간에 30개까지
 *       만들 수 있어(ReviewLoginLimiter) 계정마다 한도만으로는 LLM 호출이 한 시간에 수백 번까지 쌓인다. 여기서 429 로 막으면 누구 한 사람이
 *       한도를 채워 그날 모든 심사위원의 편성을 막을 수 있어서, 막지 않고 LLM 없이 짜는 쪽으로 돌린다.
 * </ul>
 * 구글 계정은 세지 않는다.
 *
 * <p>통과한 요청만 센다. 셈은 이 프로세스의 메모리다(서버 한 대 기준, 재시작하면 빈다) — ReviewLoginLimiter 와 같다.
 */
@Component
public class ReviewRunQuota {
    static final int MAX_RUNS_PER_DAY = 20;
    static final int MAX_AI_RUNS_PER_DAY = 400;

    /** 이번 편성을 누가 짜는지. */
    public enum Planner {
        /** AI(LLM)에 요청한다. */
        AI,
        /** 심사용 계정 모두의 오늘 AI 몫이 끝나 AI 를 부르지 않고 라벨로 짠다. */
        LABELS
    }

    private final AccountQuery accounts;
    private final AppTime time;
    private final int maxRunsPerAccount;
    private final int maxAiRunsOfAll;
    private final Map<UUID, Count> counts = new HashMap<>();
    private LocalDate aiDay = LocalDate.MIN;
    private int aiRuns;

    private record Count(LocalDate day, int runs) {}

    @Autowired
    public ReviewRunQuota(AccountQuery accounts, AppTime time) {
        this(accounts, time, MAX_RUNS_PER_DAY, MAX_AI_RUNS_PER_DAY);
    }

    ReviewRunQuota(AccountQuery accounts, AppTime time, int maxRunsPerAccount, int maxAiRunsOfAll) {
        this.accounts = accounts;
        this.time = time;
        this.maxRunsPerAccount = maxRunsPerAccount;
        this.maxAiRunsOfAll = maxAiRunsOfAll;
    }

    /**
     * 심사용 계정이면 오늘 셈에 하나를 더하고, 모두 합친 AI 몫이 남았으면 AI, 끝났으면 LABELS 를 준다. 구글 계정은 늘 AI 다.
     * 그 계정이 이미 하루 한도면 세지 않고 {@link ReviewRunLimitException}.
     */
    public Planner acquire(UUID userId) {
        if (!accounts.isReviewAccount(userId)) return Planner.AI;
        LocalDate today = time.today();
        synchronized (counts) {
            counts.values().removeIf(it -> !it.day().equals(today));
            Count count = counts.getOrDefault(userId, new Count(today, 0));
            if (count.runs() >= maxRunsPerAccount) throw new ReviewRunLimitException(maxRunsPerAccount);
            counts.put(userId, new Count(today, count.runs() + 1));
            if (!aiDay.equals(today)) {
                aiDay = today;
                aiRuns = 0;
            }
            if (aiRuns >= maxAiRunsOfAll) return Planner.LABELS;
            aiRuns++;
            return Planner.AI;
        }
    }
}
