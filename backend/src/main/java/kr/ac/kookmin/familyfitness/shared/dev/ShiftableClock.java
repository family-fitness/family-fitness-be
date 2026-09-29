package kr.ac.kookmin.familyfitness.shared.dev;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 앞으로 옮길 수 있는 서버 시계. 바탕 시계(시스템 시계)에 옮긴 폭을 더한 시각을 주고, 옮긴 뒤에도 바탕 시계를 따라 흐른다.
 * {@link #withZone} 으로 만든 시계도 같은 폭을 나눠 가져서 같이 옮겨진다. 개발용 시간 이동({@link TimeTravel})만 쓴다.
 */
public final class ShiftableClock extends Clock {
    private final Clock base;
    private final AtomicReference<Duration> offset;
    private final ZoneId zone;

    public ShiftableClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO), base.getZone());
    }

    private ShiftableClock(Clock base, AtomicReference<Duration> offset, ZoneId zone) {
        this.base = base;
        this.offset = offset;
        this.zone = zone;
    }

    /** 지금이 {@code target} 이 되게 옮긴다. 방향은 여기서 막지 않는다 — 앞으로만 가는 규칙은 {@link TimeTravel} 이 지킨다. */
    void setInstant(Instant target) {
        offset.set(Duration.between(base.instant(), target));
    }

    /** 바탕 시계에서 옮긴 폭. */
    public Duration offset() {
        return offset.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ShiftableClock(base, offset, zone);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
