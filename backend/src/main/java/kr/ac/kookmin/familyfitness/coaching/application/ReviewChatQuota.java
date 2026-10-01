package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.ReviewChatLimitException;
import kr.ac.kookmin.familyfitness.identity.api.AccountQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 심사용 계정의 코치 대화(POST /coach/chat) 횟수를 하루(KST)마다 센다. 대화마다 AI 가 LLM 을 부를 수 있는데, 심사용 계정은 구글 계정 없이
 * 누구나 만들 수 있다. 편성 한도({@link ReviewRunQuota})는 대화를 세지 않아, 한도가 없으면 LLM 비용에 끝이 없다. 한도는 둘이다.
 * <ul>
 *   <li>한 계정이 하루에 {@link #MAX_CHATS_PER_DAY} 번을 넘기면 429 TOO_MANY 다.
 *   <li>심사용 계정을 모두 합쳐 하루에 {@link #MAX_CHATS_OF_ALL_PER_DAY} 번을 넘기면, 그날 남은 심사용 대화는 모두 429 TOO_MANY 다. 계정은
 *       한 IP 에서 한 시간에 60개까지 만들 수 있어 계정마다 한도만으로는 부족하다.
 * </ul>
 * 편성과 달리 모두 합친 한도를 넘기면 429 로 막는다. AI 에 「LLM 없이 자료 문장만으로 답하라」 고 부탁할 칸이 없고, FE 에 대화 화면이 없어
 * 누가 한도를 채워도 심사위원이 화면에서 막히는 곳이 없다. 구글 계정은 세지 않는다.
 *
 * <p>AI 를 부르기 전에 센다(AI 가 실패해도 센다 — LLM 은 이미 불렸을 수 있다). 셈은 이 프로세스의 메모리다(서버 한 대 기준, 재시작하면 빈다).
 */
@Component
public class ReviewChatQuota {
    static final int MAX_CHATS_PER_DAY = 30;
    static final int MAX_CHATS_OF_ALL_PER_DAY = 300;

    private final AccountQuery accounts;
    private final AppTime time;
    private final int maxPerAccount;
    private final int maxOfAll;
    private final Map<UUID, Integer> counts = new HashMap<>();
    private LocalDate day = LocalDate.MIN;
    private int all;

    @Autowired
    public ReviewChatQuota(AccountQuery accounts, AppTime time) {
        this(accounts, time, MAX_CHATS_PER_DAY, MAX_CHATS_OF_ALL_PER_DAY);
    }

    ReviewChatQuota(AccountQuery accounts, AppTime time, int maxPerAccount, int maxOfAll) {
        this.accounts = accounts;
        this.time = time;
        this.maxPerAccount = maxPerAccount;
        this.maxOfAll = maxOfAll;
    }

    /** 심사용 계정이면 오늘 셈에 하나를 더한다. 그 계정이나 모두 합친 셈이 한도면 세지 않고 {@link ReviewChatLimitException}. */
    public void acquire(UUID userId) {
        if (!accounts.isReviewAccount(userId)) return;
        LocalDate today = time.today();
        synchronized (counts) {
            if (!day.equals(today)) {
                day = today;
                all = 0;
                counts.clear();
            }
            int mine = counts.getOrDefault(userId, 0);
            if (mine >= maxPerAccount) throw ReviewChatLimitException.perAccount(maxPerAccount);
            if (all >= maxOfAll) throw ReviewChatLimitException.ofAll();
            counts.put(userId, mine + 1);
            all++;
        }
    }
}
