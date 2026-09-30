package kr.ac.kookmin.familyfitness.shared.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShiftableClockTest {
    private static final Instant REAL = Instant.parse("2026-09-29T01:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("옮기기 전에는 바탕 시계와 같다")
    void 옮기기_전에는_바탕_시계와_같다() {
        ShiftableClock clock = new ShiftableClock(Clock.fixed(REAL, ZoneOffset.UTC));

        assertThat(clock.instant()).isEqualTo(REAL);
        assertThat(clock.offset()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("옮긴 시각을 주고, 바탕 시계가 흐르면 같이 흐른다")
    void 옮긴_시각을_주고_바탕_시계가_흐르면_같이_흐른다() {
        MovingClock base = new MovingClock(REAL);
        ShiftableClock clock = new ShiftableClock(base);
        Instant target = Instant.parse("2026-10-01T22:30:00Z");

        clock.setInstant(target);
        base.now = REAL.plusSeconds(90);

        assertThat(clock.instant()).isEqualTo(target.plusSeconds(90));
        assertThat(clock.offset()).isEqualTo(Duration.between(REAL, target));
    }

    @Test
    @DisplayName("withZone 으로 만든 시계도 같이 옮겨진다")
    void withZone_으로_만든_시계도_같이_옮겨진다() {
        ShiftableClock clock = new ShiftableClock(Clock.fixed(REAL, ZoneOffset.UTC));
        Clock seoul = clock.withZone(SEOUL);
        Instant target = REAL.plus(Duration.ofDays(3));

        clock.setInstant(target);

        assertThat(seoul.instant()).isEqualTo(target);
        assertThat(seoul.getZone()).isEqualTo(SEOUL);
    }

    /** 테스트가 「지금」을 움직이는 바탕 시계. */
    private static final class MovingClock extends Clock {
        Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
}
