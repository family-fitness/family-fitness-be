package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.ReviewChatLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewChatQuotaTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 시험에서 앞으로 옮기는 시계. */
    private static final class MovingClock extends Clock {
        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final UUID google = UUID.randomUUID();
    // 2026-09-09 23:30 KST
    private final MovingClock clock = new MovingClock(Instant.parse("2026-09-09T14:30:00Z"));
    private final ReviewChatQuota quota = new ReviewChatQuota(id -> !id.equals(google), new AppTime(clock, KST));

    @Test
    @DisplayName("심사용 계정은 하루(KST)에 코치 대화 30번까지, 날짜가 바뀌면 다시 된다")
    void 심사용_계정은_하루에_30번까지() {
        UUID review = UUID.randomUUID();
        for (int i = 0; i < ReviewChatQuota.MAX_CHATS_PER_DAY; i++) quota.acquire(review);

        assertThatThrownBy(() -> quota.acquire(review))
                .isInstanceOf(ReviewChatLimitException.class)
                .hasMessageContaining("30번");
        assertThatCode(() -> quota.acquire(UUID.randomUUID())).doesNotThrowAnyException();

        clock.now = Instant.parse("2026-09-09T15:00:00Z"); // 9/10 00:00 KST
        assertThatCode(() -> quota.acquire(review)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("심사용 계정을 모두 합쳐 하루(KST) 300번을 넘기면 새 계정도 429 — 계정을 새로 만들어 가며 LLM 을 부르지 못한다")
    void 모두_합쳐_하루_300번() {
        for (int i = 0; i < ReviewChatQuota.MAX_CHATS_OF_ALL_PER_DAY; i++) quota.acquire(UUID.randomUUID());

        assertThatThrownBy(() -> quota.acquire(UUID.randomUUID())).isInstanceOf(ReviewChatLimitException.class);
        // 구글 계정은 세지 않는다
        assertThatCode(() -> quota.acquire(google)).doesNotThrowAnyException();

        clock.now = Instant.parse("2026-09-09T15:00:00Z"); // 9/10 00:00 KST
        assertThatCode(() -> quota.acquire(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("구글 계정은 세지 않는다")
    void 구글_계정은_세지_않는다() {
        for (int i = 0; i < ReviewChatQuota.MAX_CHATS_OF_ALL_PER_DAY * 2; i++) quota.acquire(google);

        assertThatCode(() -> quota.acquire(google)).doesNotThrowAnyException();
    }
}
