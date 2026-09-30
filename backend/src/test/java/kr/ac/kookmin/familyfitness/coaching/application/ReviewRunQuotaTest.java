package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.ReviewRunLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewRunQuotaTest {
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

    private final UUID review = UUID.randomUUID();
    private final UUID google = UUID.randomUUID();
    // 2026-09-09 23:30 KST
    private final MovingClock clock = new MovingClock(Instant.parse("2026-09-09T14:30:00Z"));
    private final ReviewRunQuota quota = new ReviewRunQuota(review::equals, new AppTime(clock, KST));

    @Test
    @DisplayName("심사용 계정은 하루(KST)에 20번까지, 날짜가 바뀌면 다시 된다. 계정마다 따로 센다")
    void 심사용_계정은_하루에_20번까지() {
        for (int i = 0; i < ReviewRunQuota.MAX_RUNS_PER_DAY; i++) quota.acquire(review);

        assertThatThrownBy(() -> quota.acquire(review)).isInstanceOf(ReviewRunLimitException.class);

        clock.now = Instant.parse("2026-09-09T15:00:00Z"); // 9/10 00:00 KST
        assertThatCode(() -> quota.acquire(review)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("심사용 계정을 모두 합쳐 하루(KST) AI 편성 한도를 채우면 그다음은 라벨 편성으로 돌린다 — 429 가 아니라 모든 심사위원이 편성은 계속 받는다")
    void 모두_합친_AI_한도를_채우면_라벨_편성으로_돌린다() {
        ReviewRunQuota all = new ReviewRunQuota(id -> !id.equals(google), new AppTime(clock, KST));

        for (int i = 0; i < ReviewRunQuota.MAX_AI_RUNS_PER_DAY; i++) {
            assertThat(all.acquire(UUID.randomUUID())).isEqualTo(ReviewRunQuota.Planner.AI);
        }

        // 새 계정이어도 오늘 AI 몫은 끝났다. 구글 계정은 세지 않고 늘 AI 다
        UUID late = UUID.randomUUID();
        assertThat(all.acquire(late)).isEqualTo(ReviewRunQuota.Planner.LABELS);
        assertThat(all.acquire(google)).isEqualTo(ReviewRunQuota.Planner.AI);
        // 라벨 편성도 그 계정의 하루 20번에는 센다
        for (int i = 1; i < ReviewRunQuota.MAX_RUNS_PER_DAY; i++) all.acquire(late);
        assertThatThrownBy(() -> all.acquire(late)).isInstanceOf(ReviewRunLimitException.class);

        clock.now = Instant.parse("2026-09-09T15:00:00Z"); // 9/10 00:00 KST
        assertThat(all.acquire(UUID.randomUUID())).isEqualTo(ReviewRunQuota.Planner.AI);
    }

    @Test
    @DisplayName("구글 계정은 세지 않는다")
    void 구글_계정은_세지_않는다() {
        for (int i = 0; i < ReviewRunQuota.MAX_RUNS_PER_DAY * 2; i++) quota.acquire(google);

        assertThatCode(() -> quota.acquire(google)).doesNotThrowAnyException();
    }
}
