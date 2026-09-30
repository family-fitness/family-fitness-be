package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** identity 의 모든 "지금"·"오늘". 나이 계산은 서비스 시간대(Asia/Seoul) 기준 날짜로 한다. */
@Component
public class IdentityClock {
    private final Clock clock;
    private final ZoneId zone;

    public IdentityClock(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }
}
