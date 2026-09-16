package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** 움직일 수 있는 고정 시각. 테스트가 {@code instant} 를 바꾸면 {@link #withZone} 으로 파생된 시계까지 같이 움직인다. */
class MutableClock extends Clock {
    private final State state;
    private final ZoneId zone;

    private MutableClock(State state, ZoneId zone) {
        this.state = state;
        this.zone = zone;
    }

    MutableClock(Instant instant) {
        this(instant, ZoneId.of("Asia/Seoul"));
    }

    MutableClock(Instant instant, ZoneId zone) {
        this(new State(instant), zone);
    }

    private static final class State {
        Instant instant;

        State(Instant instant) {
            this.instant = instant;
        }
    }

    void setInstant(Instant value) {
        state.instant = value;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(state, zone);
    }

    @Override
    public Instant instant() {
        return state.instant;
    }

    IdentityClock identityClock() {
        return new IdentityClock(this, zone);
    }
}
